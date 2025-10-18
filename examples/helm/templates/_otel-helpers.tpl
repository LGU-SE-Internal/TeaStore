{{/*
OpenTelemetry Pod Annotations
This template generates the necessary annotations for OpenTelemetry auto-instrumentation
*/}}
{{- define "teastore.otel.podAnnotations" -}}
{{- if .Values.opentelemetry.enabled }}
instrumentation.opentelemetry.io/inject-{{ .Values.opentelemetry.language }}: "true"
{{- end }}
{{- end }}

{{/*
OpenTelemetry Instrumentation Reference
*/}}
{{- define "teastore.otel.instrumentationName" -}}
{{- .Values.opentelemetry.instrumentationName }}
{{- end }}
