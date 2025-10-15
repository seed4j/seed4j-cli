#!/usr/bin/env bash

until [[ "$(curl -s $JHIPSTER_SONAR_URL/api/system/status | jq -r .status)" = "UP" ]]; do
  sleep 5
done

curl -sS -u admin:admin -X POST \
  "$JHIPSTER_SONAR_URL/api/users/change_password" \
  -d "login=admin&previousPassword=admin&password=$JHIPSTER_SONAR_PASSWORD"

SONAR_TOKEN=$(curl -sS -u admin:$JHIPSTER_SONAR_PASSWORD -X POST "$JHIPSTER_SONAR_URL/api/user_tokens/generate" -d "name=$JHIPSTER_SONAR_TOKENNAME" | jq -r .token)
echo $SONAR_TOKEN
