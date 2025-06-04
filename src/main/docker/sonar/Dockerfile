FROM ubuntu:24.04
RUN \
  apt update && \
  apt --no-install-recommends install -y curl jq && \
  groupadd jhipster && \
  useradd jhipster -s /bin/bash -m -g jhipster -G sudo && \
  echo 'jhipster:jhipster'|chpasswd && \
  apt clean
ENV \
  JHIPSTER_SONAR_URL=http://sonar:9000 \
  JHIPSTER_SONAR_PASSWORD=JHipsterLite-7471 \
  JHIPSTER_SONAR_TOKENNAME=jhlitetoken
COPY sonar/sonar_generate_token.sh /usr/local/bin/sonar_generate_token
RUN chmod 755 /usr/local/bin/sonar_generate_token
USER jhipster
CMD ["sonar_generate_token"]
