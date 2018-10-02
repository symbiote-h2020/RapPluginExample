#!/bin/bash

pathFileName=$(ls build/libs/RapPluginExample*.jar)
fileName=$(basename "$pathFileName")
fileNameWithOutExtension=${fileName%.*}
version=${fileNameWithOutExtension#RapPluginExample-} 
#echo $version

docker build -t symbioteh2020/rap-plugin-example --build-arg COMPONENT_BUILD_VERSION=$version .
