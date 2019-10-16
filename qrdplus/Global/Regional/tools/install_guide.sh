#!/bin/bash
# Copyright (c) 2015 Qualcomm Technologies, Inc.
# All Rights Reserved.
# Confidential and Proprietary - Qualcomm Technologies, Inc.

source build/envsetup.sh >>/dev/null

LOCAL_BINARY_RESOURCES_PATH=
InstallCarrier_MainPackList=
InstallCarrier_PresetPackList=
InstallCarrier_OTASourcePackList=
InstallCarrier_OTABinaryPackList=

QCPATH=vendor/qcom/proprietary

function parseToSelect() {
    echo $2

    declare -a TARGETS=($1)
    SELECTED=""
    for item in ${!TARGETS[@]}
        do echo -ne " ${item}:${TARGETS[$item]}; "
    done
    echo -ne "\n"

    echo $3

    read CHOOSED
    if [ "${CHOOSED}##" = "##" ]; then
        echo "Nothing is choosed"
        return
    fi

    for item in ${CHOOSED} ; do
        if [[ ! ${item} =~ ^[0-9]+$ ]]; then
            echo "Error: Choose the number ahead of the choice."
            exit 1
        fi
        SELECTED="${SELECTED} ${TARGETS[$item]}"
    done

    SELECTED=`echo -e "${SELECTED// /\\n}" | sort | uniq`
}

function findDependency() {
    croot
    local target=$1
    # The workaround for "TMobile"->"TMO" path issue part 1.
    target=${target/TMO/TMobile}
    DEPENDENCY=""

    if [ ${DEBUG-0} = 1 ] ; then
        echo "findDependency(): target = ${target}"
    fi

    local path=`find ${QCPATH}/qrdplus -name "${target}"`
    local item
    for item in ${path} ; do
        if [ ${DEBUG-0} = 1 ] ; then
            echo "findDependency(): item = ${item}"
        fi
        if [ -e ${item}/config/.preloadspec ] ; then
            DEPENDENCY=`grep "\(^[ ]*\|^[^#].*\)Dependency="  ${item}/config/.preloadspec \
            | sed 's/.*Dependency="\([^"]*\)".*/\1/'`
            break
        fi
    done
    DEPENDENCY=${DEPENDENCY##*/}
}

function fixTargetName() {
    local ret=""
    local item
    for item in $1 ; do
        if [ ${DEBUG-0} = 1 ] ; then
            echo "fixTargetName(): item = ${item}"
        fi
        if [ "${item}##" != "##" ] ; then
            findDependency ${item}
            if [ "${DEPENDENCY}##" != "##" ] ; then
                case $2 in
                    "BINARY" | "SOURCE")
                        ret="${ret} ${DEPENDENCY}_${item}"
                        ;;
                    "PRESET")
                        ret="${ret} ${DEPENDENCY} ${item}"
                        ;;
                    "MAIN"|*)
                        ret="${DEPENDENCY} ${item}"
                        ;;
                esac
            else
                ret="${ret} ${item}"
            fi
        fi
    done

    ret=`echo -e "${ret// /\\n}" | sort | uniq`

    for item in ${ret} ; do
        case $2 in
            "BINARY")
                InstallCarrier_OTABinaryPackList="${InstallCarrier_OTABinaryPackList} ${item}"
                ;;
            "SOURCE")
                InstallCarrier_OTASourcePackList="${InstallCarrier_OTASourcePackList} ${item}"
                ;;
            "PRESET")
                InstallCarrier_PresetPackList="${InstallCarrier_PresetPackList} ${item}"
                ;;
            "MAIN")
                InstallCarrier_MainPackList="${InstallCarrier_MainPackList} ${item}"
                ;;
        esac
    done
}


#0. A workaround for carrier install issue
croot
# The workaround for ectool check issue
mkdir -p ${PWD}/tmp/bin
touch ${PWD}/tmp/bin/ectool
chmod a+x ${PWD}/tmp/bin/ectool
export PATH=${PATH}:${PWD}/tmp/bin

#1. Prepare needed binary resources like pre-install datas or mbn fils
croot
echo "1. Prepare needed binary resources like pre-install datas or mbn fils"
echo "Please input your the zip file of remote SMB servier(or local path) of binary resources,
where there files are exactly as the same hierarchy as Package required.
    e.g. smb://XXXX/YYYY/resForM.zip
Press enter with empty line will skip this step and move to next step."
echo "ZIP SMB link/local path of binary res: "

read BINARY_RESOURCES_PATH
if [ "${BINARY_RESOURCES_PATH}##" != "##" ] ; then
    echo "BINARY_RESOURCES_PATH=${BINARY_RESOURCES_PATH}"
    if [ -e ./tmp/.res ] ; then
        rm -rf ./tmp/.res
    fi
    mkdir -p ./tmp/.res

    if [ "${BINARY_RESOURCES_PATH:0:4}" = "smb:" ] ; then
        echo -n "Please input your domain(e.g. AP): "
        read LOGIN_DOMAIN
        if [ "${LOGIN_DOMAIN}##" != "##" ] ; then
            WORKGROUP="-w ${LOGIN_DOMAIN}"
        fi
        cd ./tmp/.res
        smbget ${WORKGROUP} ${BINARY_RESOURCES_PATH}
        if [ $? != 0 ] ; then
            echo "Fail to download ${BINARY_RESOURCES_PATH}"
            exit 1
        fi

        echo "Download DONE"

        ZIPFILE_NAME=${BINARY_RESOURCES_PATH##*/}
        unzip ${ZIPFILE_NAME}
        if [ $? != 0 ] ; then
            echo "Fail to unzip ${ZIPFILE_NAME}"
            exit 1
        fi

        echo "unzip DONE"

        if [ -d ${ZIPFILE_NAME%%.*} ] ; then
            cd ${ZIPFILE_NAME%%.*}
            BINARY_RESOURCES_PATH=${PWD}
        else
            BINARY_RESOURCES_PATH=${PWD}
        fi
    fi

    croot
    BINARY_RESOURCES_PATH=${BINARY_RESOURCES_PATH%%/}
    echo "Copying ..."
    cp -rf ${BINARY_RESOURCES_PATH}/* ${QCPATH}/qrdplus/Regional/
    rm -rf ./tmp/.res
fi

echo -e "1. DONE \n\n"

#2. Find all the packages and guide user to select
echo "2. Find all the packages and guide user to select which package to build"

croot
# sed 's/TMobile/TMO/' is the workaround for "TMobile"->"TMO" path issue part 2.
ALLDATAPACKAGES=`find ${QCPATH}/qrdplus/ -iname ".preloadspec" \
    | sed 's/TMobile/TMO/' \
    | sed 's/.*\/\(.*\)\/config\/.preloadspec/\1/' | grep -v "\<Extension\>" | sort | uniq`

parseToSelect "${ALLDATAPACKAGES}" "2.a All the packages available in the QGP:" "Choose the target Binary OTA package(s):"
if [ ${DEBUG-0} = 1 ] ; then
    echo "InstallCarrier_OTABinaryPackList: ${SELECTED}"
fi
fixTargetName "${SELECTED}" "BINARY"
echo -e "InstallCarrier_OTABinaryPackList: ${InstallCarrier_OTABinaryPackList}\n"

parseToSelect "${ALLDATAPACKAGES}" "2.b All the packages available in the QGP:" "Choose the target Source OTA package(s):"
if [ ${DEBUG-0} = 1 ] ; then
    echo "InstallCarrier_OTASourcePackList: ${SELECTED}"
fi
fixTargetName "${SELECTED}" "SOURCE"
echo -e "InstallCarrier_OTASourcePackList: ${InstallCarrier_OTASourcePackList}\n"

parseToSelect "${ALLDATAPACKAGES}" "2.c All the packages available in the QGP:" "Choose the target Preset OTA package(s):"
if [ ${DEBUG-0} = 1 ] ; then
    echo "InstallCarrier_PresetPackList: ${SELECTED}"
fi
fixTargetName "${SELECTED}" "PRESET"
echo -e "InstallCarrier_PresetPackList: ${InstallCarrier_PresetPackList}\n"

parseToSelect "Default ${ALLDATAPACKAGES}" "2.d All the packages available in the QGP:" "Choose ONE target OTA package as default[Default]: "
if [ ${DEBUG-0} = 1 ] ; then
    echo "InstallCarrier_MainPackList: ${SELECTED}"
fi
fixTargetName "${SELECTED}" "MAIN"
if [ "${InstallCarrier_MainPackList}##" = "##" ] ; then
    echo "If do not need to build Default.zip, please copy your Default.zip to OTA_Target_Files manually."
    echo -ne "Build Default or not? [YES]: "
    read USER_INPUT
    case "${USER_INPUT}" in
        "NO" | "No" | "no" | "N" | "n" | "not" | "NOT" | "Not" )
            InstallCarrier_MainPackList=""
            echo "InstallCarrier_MainPackList: "
            ;;
        "YES" | "yes" | "Yes" | "y" | "Y" | * )
            InstallCarrier_MainPackList="Default"
            echo "InstallCarrier_MainPackList: Default"
            ;;
    esac
else
    echo "InstallCarrier_MainPackList: ${InstallCarrier_MainPackList}"
fi
echo -e "2. DONE \n\n"

#3. Install QGP target to AMSS Build Scripts
echo "3. Install QGP target to AMSS Build Scripts"
sed -i "s/InstallCarrier_MainPackList :=.*/InstallCarrier_MainPackList := ${InstallCarrier_MainPackList}/"  ${QCPATH}/qrdplus/Extension/config/Android.mk
sed -i "s/InstallCarrier_PresetPackList :=.*/InstallCarrier_PresetPackList := ${InstallCarrier_PresetPackList}/"  ${QCPATH}/qrdplus/Extension/config/Android.mk
sed -i "s/InstallCarrier_OTASourcePackList :=.*/InstallCarrier_OTASourcePackList := ${InstallCarrier_OTASourcePackList}/"  ${QCPATH}/qrdplus/Extension/config/Android.mk
sed -i "s/InstallCarrier_OTABinaryPackList :=.*/InstallCarrier_OTABinaryPackList := ${InstallCarrier_OTABinaryPackList}/"  ${QCPATH}/qrdplus/Extension/config/Android.mk
croot
echo -e "3. DONE\n\n"

#4. Launch the AMSS build scripts to build
echo "4. Launch the AMSS build scripts to build"
croot
if [ -e device/qcom/msm8952_64 ] ; then
    TARGET_PRODUCT=msm8952_64
elif [ -e device/qcom/msm8909 ] ; then
    TARGET_PRODUCT=msm8909
else
    TARGET_PRODUCT=`find device/qcom/ -maxdepth 1  -iname "msm*" -type d | cut -f3 -d/`
    if [ "${TARGET_PRODUCT}##" != "##" ] ; then
        parseToSelect ${TARGET_PRODUCT} "Choose ONE product to build:"
        TARGET_PRODUCT=${SELECTED}
    else
        echo "We could not find any msm* product under device/qcom/"
        echo "Please input the product:"
        read TARGET_PRODUCT
    fi
fi

if [ "${TARGET_PRODUCT}##" != "##" ] ; then
    echo "The build job will start within seconds,
    and you can cancel the build job by pressing CONTROL-C"
    sleep 5
    ./build.sh -j8 ${TARGET_PRODUCT}
else
    echo "Error: No valiad product to build"
fi
