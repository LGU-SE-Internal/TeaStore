{{/*
Expand the name of the chart.
*/}}
{{- define "teastore.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "teastore.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "teastore.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "teastore.labels" -}}
helm.sh/chart: {{ include "teastore.chart" . }}
{{ include "teastore.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "teastore.selectorLabels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: teastore
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app: teastore
version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "teastore.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "teastore.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
HTTP Load Generator helpers
*/}}
{{- define "teastore.httploadgen.fullname" -}}
{{- printf "%s-httploadgen" (include "teastore.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "teastore.httploadgen.selectorLabels" -}}
{{ include "teastore.selectorLabels" . }}
app.kubernetes.io/component: teastore-httploadgen
{{- end }}

{{/*
HTTP Load Director helpers
*/}}
{{- define "teastore.httploaddirector.fullname" -}}
{{- printf "%s-httploaddirector" (include "teastore.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "teastore.httploaddirector.selectorLabels" -}}
{{ include "teastore.selectorLabels" . }}
app.kubernetes.io/component: teastore-httploaddirector
{{- end }}

{{/*
JMeter helpers
*/}}
{{- define "teastore.jmeter.fullname" -}}
{{- printf "%s-jmeter" (include "teastore.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "teastore.jmeter.labels" -}}
{{ include "teastore.labels" . }}
app.kubernetes.io/component: teastore-jmeter
{{- end }}

{{- define "teastore.jmeter.selectorLabels" -}}
{{ include "teastore.selectorLabels" . }}
app.kubernetes.io/component: teastore-jmeter
{{- end }}

{{/*
Image helpers - construct full image reference with global fallback
Usage: {{ include "teastore.image" (dict "imageConfig" .Values.webui.image "global" .Values.global "Chart" .Chart) }}
*/}}
{{- define "teastore.image" -}}
{{- $registry := .imageConfig.registry | default .global.image.registry -}}
{{- $tag := .imageConfig.tag | default .global.image.tag | default .Chart.AppVersion -}}
{{- printf "%s/%s:%s" $registry .imageConfig.name $tag -}}
{{- end }}

{{/*
Image pull policy helper with global fallback
Usage: {{ include "teastore.imagePullPolicy" (dict "imageConfig" .Values.webui.image "global" .Values.global) }}
*/}}
{{- define "teastore.imagePullPolicy" -}}
{{- .imageConfig.pullPolicy | default .global.image.pullPolicy | default "IfNotPresent" -}}
{{- end }}

{{/*
Init container to wait for all services to be registered in the registry
*/}}
{{- define "teastore.initContainer.waitForRegistry" -}}
- name: wait-for-registry
  image: busybox:latest
  command:
    - sh
    - -c
    - |
      echo "Waiting for all services to be registered in registry..."
      REGISTRY_HOST="{{ template "teastore.registry.url" . }}"
      REGISTRY_PORT="{{ .Values.registry.service.port }}"
      REQUIRED_SERVICES="tools.descartes.teastore.persistence tools.descartes.teastore.auth tools.descartes.teastore.image tools.descartes.teastore.recommender tools.descartes.teastore.webui"
      
      # Wait for registry service to be available
      until nc -z -v -w5 $REGISTRY_HOST $REGISTRY_PORT; do
        echo "Registry not available, waiting..."
        sleep 2
      done
      echo "Registry is available, checking service registration..."
      
      # Check if all required services are registered
      while true; do
        all_registered=true
        for service in $REQUIRED_SERVICES; do
          response=$(wget -q -O- http://$REGISTRY_HOST:$REGISTRY_PORT/tools.descartes.teastore.registry/rest/services/$service 2>/dev/null)
          # Check if response is not empty and contains at least one service instance
          if [ -z "$response" ] || [ "$response" = "[]" ]; then
            echo "Service $service not yet registered, waiting..."
            all_registered=false
            break
          fi
        done
        
        if [ "$all_registered" = true ]; then
          echo "All required services are registered!"
          break
        fi
        sleep 5
      done
{{- end }}
