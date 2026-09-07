import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'
export function Alert({ className, ...props }: ComponentProps<'div'>) { return <div role="alert" data-slot="alert" className={cn('notice', className)} {...props} /> }
export function AlertTitle(props: ComponentProps<'h3'>) { return <h3 {...props} /> }
export function AlertDescription(props: ComponentProps<'div'>) { return <div {...props} /> }
