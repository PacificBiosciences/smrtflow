#!/bin/bash

while [[ $# -gt 1 ]]
do
key="$1"

case $key in
  -h|--host)
  host="$2"
  shift
  ;;
  -o|--port_offset)
  port_offset="$2"
  shift
  ;;
  -r|--provider)
  provider="$2"
  shift
  ;;
  -u|--username)
  username="$2"
  shift
  ;;
  -p|--password)
  password="$2"
  shift
  ;;
  -k|--key)
  consumer_key="$2"
  shift
  ;;
  -s|--secret)
  consumer_secret="$2"
  shift
  ;;
  -a|--api)
  api="$2"
  shift
  ;;
  -c|--scope)
  scope="$2"
  shift
  ;;
  -X)
  method="$2"
  shift
  ;;
  *)
  # unknown option
  ;;
esac
shift # past argument or value
done

if [ -z $host ]; then
  host="localhost"
fi

if [ -z $port_offset ]; then
  port_offset=0
fi

if [ -z $username ]; then
  echo -n "WSO2 username: "
  read username
fi

if [ -z $password ]; then
  echo -n "WSO2 password: "
  read -s password
  echo
fi

if [ -z $provider ]; then
  echo -n "API provider username: "
  read provider
fi

# TODO(smcclellan): Can the consumer key/secret be obtained via APIs?
if [ -z $consumer_key ]; then
  echo -n "DefaultApplication consumer key: "
  read consumer_key
fi

if [ -z $consumer_secret ]; then
  echo -n "DefaultApplication consumer secret: "
  read -s consumer_secret
  echo
fi

basic_auth=$(echo -n "$consumer_key:$consumer_secret" | base64 -w 0)

if [ -z $api ]; then
  echo -n "API name: "
  read api
fi

if [ -z $method ]; then
  method="GET"
fi

cookie_file="cookies.tmp"

get_uri() {
  port=$(($2+$port_offset))
  echo "$1://$host:$port$3"
}

do_curl() {
  eval "curl -s -k -c $cookie_file -b $cookie_file $@"
}

from_json() {
  json=$1
  regex="(?<=\"$2\":\")[^\"]*"
  value=$(echo "$json" | grep -Po "$regex")
  echo $value
}

# Login to store

login_uri=$(get_uri "http" 9763 "/store/site/blocks/user/login/ajax/login.jag")
login_params="action=login"
login_params="$login_params&username=$username"
login_params="$login_params&password=$password"
login_resp=$(do_curl -X "POST" -d "'$login_params'" $login_uri)

# Subscribe to API

# TODO(smcclellan): Check if the application is already subscribed first?
subscribe_uri=$(get_uri "http" 9763 "/store/site/blocks/subscription/subscription-add/ajax/subscription-add.jag")
subscribe_params="action=addAPISubscription"
subscribe_params="$subscribe_params&name=$api"
subscribe_params="$subscribe_params&version=1.0.0"
subscribe_params="$subscribe_params&provider=$provider"
subscribe_params="$subscribe_params&tier=Unlimited"
subscribe_params="$subscribe_params&applicationName=DefaultApplication"
subscribe_resp=$(do_curl -X "POST" -d "'$subscribe_params'" $subscribe_uri)
echo "Subscribe Response: $subscribe_resp"

# Get auth token

get_token_uri=$(get_uri "https" 8243 "/token")
get_token_params="grant_type=password"
get_token_params="$get_token_params&username=$username"
get_token_params="$get_token_params&password=$password"
if [ ! -z $scope ]; then
  # TODO(smcclellan): This isn't working for some reason. The returned token always has default scope.
  get_token_params="$get_token_params&scope=$scope"
fi 
get_token_resp=$(do_curl -d "'$get_token_params'" -H "'Authorization: Basic $basic_auth'" $get_token_uri)
echo "Get Token Resp: $get_token_resp"
token=$(from_json $get_token_resp "access_token")
echo "Token: $token"

# Execute call to endpoint

execute_uri=$(get_uri "https" 8243 "/$api/1.0.0$1")
execute_resp=$(do_curl -H "'Authorization: Bearer $token'" $execute_uri)
echo "Execute Resp $execute_resp"

# Cleanup cookies file

rm $cookie_file
