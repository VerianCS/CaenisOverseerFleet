import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'
export function FieldGroup({ className, ...props }: ComponentProps<'div'>) { return <div data-slot="field-group" className={cn('field-group', className)} {...props} /> }
export function Field({ className, ...props }: ComponentProps<'div'>) { return <div data-slot="field" className={cn('field', className)} {...props} /> }
export function FieldLabel(props: ComponentProps<'label'>) { return <label data-slot="field-label" {...props} /> }
export function FieldDescription(props: ComponentProps<'p'>) { return <p data-slot="field-description" className="muted" {...props} /> }
