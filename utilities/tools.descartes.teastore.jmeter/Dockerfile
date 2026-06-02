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
ENV NUM_USERS="2"
ENV RAMP_UP="10"
ENV DURATION="300"
ENV TEST_PLAN="teastore_browse_nogui.jmx"

# Run JMeter directly with Java. Shell form so NUM_USERS/HOSTNAME/PORT/RAMP_UP
# from the environment actually reach the test plan as JMeter properties.
CMD java -jar /opt/apache-jmeter-${JMETER_VERSION}/bin/ApacheJMeter.jar \
     -n \
     -t "/jmeter/testplans/${TEST_PLAN}" \
     -Jhostname="${HOSTNAME}" \
     -Jport="${PORT}" \
     -JNUM_USERS="${NUM_USERS}" \
     -JnumUser="${NUM_USERS}" \
     -JrampUp="${RAMP_UP}" \
     -l /jmeter/results/results.jtl
