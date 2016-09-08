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
  -u|--username)
  username="$2"
  shift
  ;;
  -p|--password)
  password="$2"
  shift
  ;;
  -n|--name)
  name="$2"
  shift
  ;;
  -t|--target)
  target="$2"
  shift
  ;;
  -s|--swagger)
  swagger_file="$2"
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
  echo -n "WSO2 admin username: "
  read username
fi

if [ -z $password ]; then
  echo -n "WSO2 admin password: "
  read -s password
  echo
fi

if [ -z $name ]; then
echo -n "API name: "
read name
fi

if [ -z $target ]; then
echo -n "Target URL: "
read target
fi

if [ -z $swagger_file ]; then
  echo -n "Path to JSON swagger file: "
  read swagger_file
fi

cookie_file="cookies.tmp"

get_uri() {
  port=$(($2+$port_offset))
  echo "$1://$host:$port$3"
}

do_curl() {
  eval "curl -s -k -c $cookie_file -b $cookie_file $@"
}

# Login to publisher

login_uri=$(get_uri "http" 9763 "/publisher/site/blocks/user/login/ajax/login.jag")
login_params="action=login"
login_params="$login_params&username=$username"
login_params="$login_params&password=$password"
login_resp=$(do_curl -X "POST" -d "'$login_params'" $login_uri)
echo "Login Response: $login_resp"

# Add API

# slurp swagger file and strip all whitespace
add_api_swagger=$(cat "$swagger_file")
add_api_swagger=${add_api_swagger//[[:space:]]/}

add_api_uri=$(get_uri "http" 9763 "/publisher/site/blocks/item-add/ajax/add.jag")
add_api_params="action=addAPI"
add_api_params="$add_api_params&name=$name"
add_api_params="$add_api_params&context=/$name"
add_api_params="$add_api_params&version=1.0.0"
add_api_params="$add_api_params&visibility=public"
add_api_params="$add_api_params&swagger=$add_api_swagger"
add_api_params="$add_api_params&endpoint_config={\"production_endpoints\":{\"url\":\"$target\",\"config\":null},\"sandbox_endpoints\":{\"url\":\"$target\",\"config\":null},\"endpoint_type\":\"http\"}"
add_api_params="$add_api_params&tiersCollection=Unlimited"
add_api_resp=$(do_curl -X "POST" -d "'$add_api_params'" $add_api_uri)
echo "Create API Response: $add_api_resp"

# Publish API

publish_uri=$(get_uri "http" 9763 "/publisher/site/blocks/life-cycles/ajax/life-cycles.jag")
publish_params="action=updateStatus"
publish_params="$publish_params&name=$name"
publish_params="$publish_params&version=1.0.0"
publish_params="$publish_params&provider=$username"
publish_params="$publish_params&status=PUBLISHED"
publish_params="$publish_params&publishToGateway=true"
publish_params="$publish_params&requireResubscription=true"
publish_resp=$(do_curl -X "POST" -d "'$publish_params'" $publish_uri)
echo "Publish API Response: $publish_resp"

# Cleanup cookies file

rm $cookie_file
