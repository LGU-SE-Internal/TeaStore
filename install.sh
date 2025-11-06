sdk env install

mvn clean install

cd utilities/tools.descartes.teastore.dockerbase

docker build -t 10.10.10.240/library/teastore-base:latest .


skaffold build --default-repo=10.10.10.240/library



helm install teastore examples/helm -n teastore --create-namespace