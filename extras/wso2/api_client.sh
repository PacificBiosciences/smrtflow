#!/bin/bash

curl_args=""

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
  -X)
  method="$2"
  shift
  ;;
  *)
  curl_args="$curl_args $1"
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

if [ -z $method ]; then
  method="GET"
fi

cookie_file="cookies.tmp"
scopes="run-qc%20run-design%20sample-setup%20data-management%20smrt-analysis"

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

# Get auth token

get_token_uri=$(get_uri "https" 8243 "/token")
get_token_params="grant_type=password"
get_token_params="$get_token_params&username=$username"
get_token_params="$get_token_params&password=$password"
get_token_params="$get_token_params&scope=$scopes"
get_token_resp=$(do_curl -d "'$get_token_params'" -H "'Authorization: Basic $basic_auth'" $get_token_uri)
echo "Get Token Resp: $get_token_resp"
token=$(from_json "'$get_token_resp'" "access_token")
echo "Token: $token"

# Execute call to endpoint

execute_uri=$(get_uri "https" 8243 "/SMRTLink/1.0.0$1")
shift
execute_resp=$(do_curl -H "'Authorization: Bearer $token'" $curl_args $execute_uri)
echo "Execute Resp: $execute_resp"

# Cleanup cookies file

rm $cookie_file
