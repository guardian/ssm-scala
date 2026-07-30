FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

# Core utilities
RUN apt-get update && apt-get install -y \
    curl unzip ca-certificates jq less groff bash socat \
    && rm -rf /var/lib/apt/lists/*

# -------------------------------
# AWS CLI v2 (official installer)
# -------------------------------
RUN curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o awscliv2.zip \
    && unzip awscliv2.zip \
    && ./aws/install \
    && rm -rf awscliv2.zip aws

# -------------------------------
# Configure AWS Region
# -------------------------------
RUN mkdir -p /root/.aws/
RUN echo "[default]\nregion = eu-west-1" | tee -a /root/.aws/config 

# -------------------------------
# Session Manager Plugin
# -------------------------------
RUN curl "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_arm64/session-manager-plugin.deb" -o session-manager-plugin.deb 
RUN dpkg -i session-manager-plugin.deb

# -------------------------------
# AWS helper scripts
# -------------------------------
COPY scripts/session /usr/local/bin/session
RUN chmod +x /usr/local/bin/session

COPY scripts/host-tunnel /usr/local/bin/host-tunnel
RUN chmod +x /usr/local/bin/host-tunnel

COPY scripts/rds-tunnel /usr/local/bin/rds-tunnel
RUN chmod +x /usr/local/bin/rds-tunnel

# -------------------------------
# Entrypoint which starts socat
# -------------------------------
COPY scripts/socat-listener /usr/local/bin/socat-listener
RUN chmod +x /usr/local/bin/socat-listener

# Default shell
ENTRYPOINT ["/usr/local/bin/socat-listener"]
