# KALM Base Build & Experiments Environment
#
# This Dockerfile creates a reproducible Linux environment for KALM builds, tests, and scientific
# experiments. It is intended for:
# - CI/CD pipelines (GitLab, etc.)
# - Local development with container isolation
# - Scientific reproducibility (experiments run in the same image as CI)
#
# The image includes:
# - Linux base (Ubuntu 22.04 LTS) with security updates
# - PowerShell 7.4+ for script automation
# - JDK (matching the project requirements)
# - Git and build essentials
# - Locale support for consistent text processing
#
# The source code is NOT baked into the image; mount or copy the repo at runtime.
# Always use ./gradlew (the Gradle wrapper) from the mounted repo; do not install standalone Gradle
# into the image.

FROM ubuntu:22.04

# Set metadata using OCI Image Spec standard keys (org.opencontainers.image.*)
# This enables standardized tooling, CI/CD integration, and image management
LABEL org.opencontainers.image.authors="Ignacio Slater-Muñoz <https://ravenhill.cl>" \
    org.opencontainers.image.title="KALM Base Build & Experiments Environment" \
    org.opencontainers.image.description="Reproducible Linux environment for KALM builds, tests, and scientific experiments" \
    org.opencontainers.image.version="0.1.0" \
    org.opencontainers.image.source="https://gitlab.com/r8vnhill/kalm" \
    org.opencontainers.image.documentation="https://gitlab.com/r8vnhill/kalm" \
    org.opencontainers.image.base.name="ubuntu:22.04" \
    org.opencontainers.image.vendor="KALM Contributors" \
    org.opencontainers.image.licenses="BSD-2-Clause"

# Copy and execute the build environment setup script
COPY scripts/docker/setup-build-environment.sh /tmp/setup-build-environment.sh
RUN chmod +x /tmp/setup-build-environment.sh && \
    /tmp/setup-build-environment.sh && \
    rm /tmp/setup-build-environment.sh

# Set environment variables for Java and locale
ENV LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/java-22-openjdk-amd64 \
    GRADLE_USER_HOME=/gradle-cache \
    GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Ensure JAVA_HOME is in PATH
ENV PATH=$JAVA_HOME/bin:$PATH

# Create a non-root user for builds (optional but recommended for security)
RUN useradd -m -s /usr/bin/pwsh builder

# Create directories for workspace and gradle cache
RUN mkdir -p /workspace /gradle-cache && \
    chown -R builder:builder /workspace /gradle-cache

# Set working directory
WORKDIR /workspace

# Switch to non-root user (optional; comment out if you prefer root)
# USER builder

# Set bash as the default shell for the container
# This allows ./gradlew and shell scripts to work by default.
SHELL ["/bin/bash", "-c"]

# Health check: verify Java and PowerShell are available
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD java -version && pwsh -Version

# PowerShell as the primary entrypoint for automation-first workflows
ENTRYPOINT ["/usr/bin/pwsh", "-NoProfile"]
