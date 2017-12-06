#!/usr/bin/env bash

# delete files not used by SL
rm -rf smrt-common-models/src/main/resources/pb-common-xsds/{PacBioDeclData.xsd,PacBioSeedingData.xsd}

# Generate Java classes
xjc smrt-common-models/src/main/resources/pb-common-xsds -d smrt-common-models/src/main/java

# Strip out Timestamps in classes
for i in $(find smrt-common-models/src/main/java/com/pacificbiosciences -name "*.java"); do
    echo $i
    sed -i .bak 's/Generated on: .*/Generated on: XXX/' $i
    rm "${i}".bak
done


# Revert package-info.java files which have special namespace bindings. This should be able to
# be done in the xjc call
git checkout -- smrt-common-models/src/main/java/com/*/*/package-info.java
