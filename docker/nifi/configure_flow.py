"""Install Caenis' signed telemetry conduit through the NiFi REST API.

Uses only Python's standard library. Run from a Conda environment.
The operator supplies NiFi's trusted certificate; TLS verification is never disabled.
This script provisions processors and connections. It does not run tests.
"""
import argparse
import json
import os
import ssl
import urllib.parse
import urllib.request
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="https://localhost:8443")
    parser.add_argument("--ca-file", required=True)
    parser.add_argument("--backend", default="http://core:8080")
    parser.add_argument("--env-file", default=".env")
    args = parser.parse_args()
    values = {}
    for line in Path(args.env_file).read_text(encoding="utf-8-sig").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    context = ssl.create_default_context(cafile=args.ca_file)
    base = args.url.rstrip("/") + "/nifi-api"
    auth_data = urllib.parse.urlencode({"username": values.get("NIFI_USERNAME", "caenis"), "password": values["NIFI_PASSWORD"]}).encode()
    token_request = urllib.request.Request(base + "/access/token", data=auth_data, headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(token_request, context=context, timeout=30) as response:
        token = response.read().decode()

    def call(method, route, payload=None):
        body = None if payload is None else json.dumps(payload).encode()
        request = urllib.request.Request(base + route, data=body, method=method,
            headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
        with urllib.request.urlopen(request, context=context, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}

    root = call("GET", "/flow/process-groups/root")["processGroupFlow"]
    parent = root["id"]
    group = next((item for item in root["flow"]["processGroups"] if item["component"]["name"] == "Caenis signed telemetry"), None)
    if group is None:
        group = call("POST", "/process-groups/" + parent + "/process-groups",
            {"revision": {"version": 0}, "component": {"name": "Caenis signed telemetry", "position": {"x": 0, "y": 0}}})
    group_id = group["id"]
    existing = call("GET", "/flow/process-groups/" + group_id)["processGroupFlow"]["flow"]
    processors = {item["component"]["name"]: item for item in existing["processors"]}
    # Stop only the processors in this dedicated group before applying configuration.
    for item in processors.values():
        if item["component"].get("state") == "RUNNING":
            call("PUT", "/processors/" + item["id"] + "/run-status", {"revision": item["revision"], "state": "STOPPED"})
    types = call("GET", "/flow/processor-types")["processorTypes"]
    service_types = call("GET", "/flow/controller-service-types")["controllerServiceTypes"]

    def kind(items, suffix):
        matches = [item for item in items if item["type"].endswith("." + suffix)]
        if len(matches) != 1:
            raise RuntimeError("Expected one installed " + suffix + " implementation")
        return matches[0]

    services = call("GET", "/flow/process-groups/" + group_id + "/controller-services")["controllerServices"]
    context_service = next((item for item in services if item["component"]["name"] == "Caenis HTTP contexts"), None)
    if context_service is None:
        descriptor = kind(service_types, "StandardHttpContextMap")
        context_service = call("POST", "/process-groups/" + group_id + "/controller-services",
            {"revision": {"version": 0}, "component": {"type": descriptor["type"], "bundle": descriptor["bundle"],
             "name": "Caenis HTTP contexts", "properties": {"Maximum Outstanding Requests": "200", "Request Expiration": "30 sec"}}})
    service_id = context_service["id"]
    service = call("GET", "/controller-services/" + service_id)
    if service["component"]["state"] != "ENABLED":
        call("PUT", "/controller-services/" + service_id + "/run-status", {"revision": service["revision"], "state": "ENABLED"})

    def add(name, suffix, properties, terminated=(), x=0, y=0):
        item = processors.get(name)
        if item is None:
            descriptor = kind(types, suffix)
            item = call("POST", "/process-groups/" + group_id + "/processors", {"revision": {"version": 0},
                "component": {"type": descriptor["type"], "bundle": descriptor["bundle"], "name": name, "position": {"x": x, "y": y}}})
        current = call("GET", "/processors/" + item["id"])
        config = {"properties": properties, "autoTerminatedRelationships": list(terminated),
                  "schedulingStrategy": "TIMER_DRIVEN", "schedulingPeriod": "100 ms", "concurrentlySchedulableTaskCount": 1,
                  "penaltyDuration": "30 sec", "yieldDuration": "1 sec"}
        updated = call("PUT", "/processors/" + item["id"], {"revision": current["revision"], "component": {"id": item["id"], "config": config}})
        processors[name] = updated
        return updated["id"]

    def header(name):
        return "${'http.headers." + name.lower() + "':replaceNull(${'http.headers." + name + "'})}"

    headers = {name: header(name) for name in ("X-Caenis-Timestamp", "X-Caenis-Nonce", "X-Caenis-Signature")}
    instance = header("X-Caenis-Instance")
    receive = add("Receive signed batch", "HandleHttpRequest", {
        "Listening Port": "8085", "Hostname": "0.0.0.0", "HTTP Context Map": service_id,
        "Allowed Paths": "/telemetry", "Allow GET": "false", "Allow POST": "true", "Allow PUT": "false",
        "Allow DELETE": "false", "Allow HEAD": "false", "Allow OPTIONS": "false", "Maximum Threads": "16", "Container Queue Size": "50"
    }, x=0, y=0)
    route = add("Route valid instance identities", "RouteOnAttribute", {
        "Routing Strategy": "Route to Property name",
        "valid": instance[:-1] + ":matches('[a-z0-9][a-z0-9-]{2,63}')}"
    }, x=0, y=180)
    invoke = {"HTTP Method": "POST", "Request Content-Type": "application/json", "Request Body Enabled": "true",
              "Response Body Ignored": "true", "Request Header Attributes Pattern": "^$",
              "Connection Timeout": "3 sec", "Read Timeout": "10 sec", **headers}
    authorize = add("Authorize raw signed payload", "InvokeHTTP", {
        **invoke, "HTTP URL": args.backend.rstrip("/") + "/api/v1/agent/" + instance + "/authorize"
    }, terminated=("Response",), x=0, y=360)
    accepted = add("Acknowledge durable conduit", "HandleHttpResponse", {
        "HTTP Status Code": "202", "HTTP Context Map": service_id
    }, terminated=("success", "failure"), x=-360, y=570)
    rejected = add("Reject unauthenticated request", "HandleHttpResponse", {
        "HTTP Status Code": "${invokehttp.status.code:replaceNull('400')}", "HTTP Context Map": service_id
    }, terminated=("success", "failure"), x=-600, y=360)
    unavailable = add("Return unavailable", "HandleHttpResponse", {
        "HTTP Status Code": "503", "HTTP Context Map": service_id
    }, terminated=("success", "failure"), x=-360, y=360)
    forward = add("Deliver unchanged signed batch", "InvokeHTTP", {
        **invoke, "HTTP URL": args.backend.rstrip("/") + "/api/v1/agent/" + instance + "/mining"
    }, terminated=("Original", "Response"), x=300, y=570)
    # Permanent failures go to a bounded, expiring queue; its processor is deliberately left stopped.
    dead_letter = add("Permanent rejection queue", "LogAttribute", {"Log Payload": "false"},
        terminated=("success",), x=650, y=750)

    connections = {(item["component"]["source"]["id"], item["component"]["destination"]["id"],
                    tuple(sorted(item["component"]["selectedRelationships"]))): item for item in existing["connections"]}
    def connect(source, destination, relationships, count=1000, size="256 MB"):
        identity = (source, destination, tuple(sorted(relationships)))
        if identity in connections:
            return
        call("POST", "/process-groups/" + group_id + "/connections", {"revision": {"version": 0}, "component": {
            "source": {"id": source, "groupId": group_id, "type": "PROCESSOR"},
            "destination": {"id": destination, "groupId": group_id, "type": "PROCESSOR"},
            "selectedRelationships": relationships, "backPressureObjectThreshold": count,
            "backPressureDataSizeThreshold": size, "flowFileExpiration": "24 hours"
        }})
    connect(receive, route, ["success"], 100, "16 MB")
    connect(route, authorize, ["valid"], 100, "16 MB")
    connect(route, rejected, ["unmatched"], 100, "16 MB")
    # Fan-out is committed as one NiFi session before these downstream processors see either copy.
    connect(authorize, accepted, ["Original"])
    connect(authorize, forward, ["Original"])
    connect(authorize, rejected, ["No Retry"], 100, "16 MB")
    connect(authorize, unavailable, ["Retry", "Failure"], 100, "16 MB")
    connect(forward, forward, ["Retry", "Failure"])
    connect(forward, dead_letter, ["No Retry"], 100, "32 MB")
    for name, item in processors.items():
        if item["id"] == dead_letter:
            continue
        current = call("GET", "/processors/" + item["id"])
        call("PUT", "/processors/" + item["id"] + "/run-status", {"revision": current["revision"], "state": "RUNNING"})
    print("Caenis signed telemetry flow configured. The permanent rejection queue remains stopped for operator inspection.")

if __name__ == "__main__":
    main()
