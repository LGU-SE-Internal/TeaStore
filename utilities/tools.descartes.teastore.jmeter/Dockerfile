ARG BASE_IMAGE=10.10.10.240/library/teastore-base:latest
FROM ${BASE_IMAGE}

# Install JMeter
ENV JMETER_VERSION=5.6.3
ENV JMETER_HOME=/opt/apache-jmeter-${JMETER_VERSION}

USER root

# Download and install JMeter
RUN cd /opt && \
    wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz && \
    tar -xzf apache-jmeter-${JMETER_VERSION}.tgz && \
    rm apache-jmeter-${JMETER_VERSION}.tgz

# Create directories for test plans and results
RUN mkdir -p /jmeter/testplans /jmeter/results

# Copy test plans
COPY teastore_browse.jmx /jmeter/testplans/teastore_browse.jmx
COPY teastore_browse_nogui.jmx /jmeter/testplans/teastore_browse_nogui.jmx
COPY teastore_browse_rps.jmx /jmeter/testplans/teastore_browse_rps.jmx

WORKDIR /jmeter

# Default environment variables
ENV HOSTNAME="teastore-webui"
ENV PORT="80"
ENV NUM_USERS="10"
ENV RAMP_UP="10"
ENV DURATION="300"
ENV TEST_PLAN="teastore_browse_nogui.jmx"

# Run JMeter directly with Java
CMD ["java", "-jar", "/opt/apache-jmeter-5.6.3/bin/ApacheJMeter.jar", \
     "-n", \
     "-t", "/jmeter/testplans/teastore_browse_nogui.jmx", \
     "-Jhostname=teastore-webui", \
     "-Jport=80", \
     "-JnumUser=10", \
     "-JrampUp=10", \
     "-l", "/jmeter/results/results.jtl"]
