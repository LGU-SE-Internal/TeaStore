{{/*
OpenTelemetry Pod Annotations
This template generates the necessary annotations for OpenTelemetry auto-instrumentation
*/}}
{{- define "teastore.otel.podAnnotations" -}}
{{- if .Values.opentelemetry.enabled }}
instrumentation.opentelemetry.io/inject-{{ .Values.opentelemetry.language }}: "monitoring/opentelemetry-kube-stack"
{{- end }}
{{- end }}

{{/*
OpenTelemetry Instrumentation Reference
*/}}
{{- define "teastore.otel.instrumentationName" -}}
{{- .Values.opentelemetry.instrumentationName }}
{{- end }}

{{/*
OpenTelemetry Environment Variables for SDK configuration
These environment variables configure the OpenTelemetry SDK for traces and logs export via OTLP
*/}}
{{- define "teastore.otel.envVars" -}}
{{- if .Values.opentelemetry.enabled }}
- name: OTEL_SERVICE_NAME
  valueFrom:
    fieldRef:
      fieldPath: metadata.labels['app.kubernetes.io/component']
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: {{ .Values.opentelemetry.otlpEndpoint | quote }}
- name: OTEL_EXPORTER_OTLP_PROTOCOL
  value: {{ .Values.opentelemetry.otlpProtocol | quote }}
{{- if .Values.opentelemetry.tracesExporterEnabled }}
- name: OTEL_TRACES_EXPORTER
  value: "otlp"
{{- end }}
{{- if .Values.opentelemetry.logsExporterEnabled }}
- name: OTEL_LOGS_EXPORTER
  value: "otlp"
{{- end }}
{{- if .Values.opentelemetry.metricsExporterEnabled }}
- name: OTEL_METRICS_EXPORTER
  value: "otlp"
{{- else }}
- name: OTEL_METRICS_EXPORTER
  value: "none"
{{- end }}
- name: OTEL_TRACES_SAMPLER
  value: "parentbased_traceidratio"
- name: OTEL_TRACES_SAMPLER_ARG
  value: {{ .Values.opentelemetry.samplingRatio | quote }}
- name: OTEL_PROPAGATORS
  value: "tracecontext,baggage,b3multi"
- name: OTEL_RESOURCE_ATTRIBUTES
  value: "service.namespace=teastore,deployment.environment=kubernetes"
- name: OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE
  value: "true"
- name: OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED
  value: "true"
{{- end }}
{{- end }}
