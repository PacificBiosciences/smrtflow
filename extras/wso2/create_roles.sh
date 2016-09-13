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

get_uri() {
  port=$(($2+$port_offset))
  echo "$1://$host:$port$3"
}

rusms_endpoint=$(get_uri "https" 9443 "/services/RemoteUserStoreManagerService.RemoteUserStoreManagerServiceHttpsSoap12Endpoint")

roles="Internal/PbAdmin Internal/PbLabTech Internal/PbBioinformatician"

for role in $roles; do
  curl --user $username:password --header "Content-Type: text/xml;charset=UTF-8" --header "SOAPAction: \"urn:addRole\"" -k -d @- $rusms_endpoint <<EOF
<?xml version='1.0' encoding='utf-8'?>
  <soap-env:Envelope xmlns:soap-env='http://schemas.xmlsoap.org/soap/envelope/'
                     xmlns:soap='http://schemas.xmlsoap.org/wsdl/soap/'
                     xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>
    <soap-env:Body xmlns:jns0='http://common.mgt.user.carbon.wso2.org/xsd'
                   xmlns:jns1='http://api.user.carbon.wso2.org/xsd'
                   xmlns:jns2='http://dao.service.ws.um.carbon.wso2.org/xsd'
                   xmlns:jns3='http://service.ws.um.carbon.wso2.org'>
      <jns3:addRole xmlns:jns0='http://common.mgt.user.carbon.wso2.org/xsd'
                    xmlns:jns1='http://api.user.carbon.wso2.org/xsd'
                    xmlns:jns2='http://dao.service.ws.um.carbon.wso2.org/xsd'
                    xmlns:jns3='http://service.ws.um.carbon.wso2.org'>
        <jns3:roleName>$role</jns3:roleName>
      </jns3:addRole>
    </soap-env:Body>
  </soap-env:Envelope>
EOF

done
