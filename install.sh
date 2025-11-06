sdk env install

mvn clean install

skaffold build --default-repo=10.10.10.240/library

helm install teastore examples/helm -n teastore --create-namespace