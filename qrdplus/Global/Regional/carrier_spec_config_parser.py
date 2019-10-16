#!/usr/bin/env python

# Copyright (c) 2015 Qualcomm Technologies, Inc.
# All Rights Reserved.
# Confidential and Proprietary - Qualcomm Technologies, Inc.

import csv
import os
import sys

DEBUG_MODE = True

def print_log(log):
    if DEBUG_MODE:
        print log

def main(argv):
    global DEBUG_MODE
    DEBUG_MODE = True
    if (len(argv) == 2):
        if (argv[1] == "-nl"):
            DEBUG_MODE = False
    print_log("############## Try to parse CarrierSpecConfig.csv #################")
    pwd = os.path.split(os.path.realpath(__file__))[0]
    print_log("pwd=" + pwd)
    packages_location = pwd
    csv_location = pwd + os.path.sep + "CarrierSpecConfig.csv"
    if not os.path.exists(csv_location):
        print_log("Can not find the CarrierSpecConfig.csv file")
        return
    with open(csv_location, "rb") as csv_file:
        csv_reader = csv.DictReader(csv_file, dialect='excel')
        if "Name" not in csv_reader.fieldnames:
            print_log("Can not find the key word \"Name\"")
        else:
            for row in csv_reader:
                package_path = os.path.join(packages_location, row["Name"])
                if not os.path.exists(package_path):
                    print_log("Can not find the \"" + row["Name"] + "\" package")
                    break
                print_log("### Try to generate .preloaspec file for \"" + row["Name"] + "\"")
                spec_path = reduce(os.path.join, [package_path, "config", ".preloadspec"])
                with open(spec_path, 'w') as spec_file:
                    for field in csv_reader.fieldnames:
                        print_log(field + "=\"" + row[field].strip(",") + "\"")
                        spec_file.write(field + "=\"" + row[field].strip(",") + "\" ")
                    print_log("Write file " + spec_path + " done.")
    print_log("############ Succeeded Parsing CarrierSpecConfig.csv ##############")

if __name__ == '__main__':
    main(sys.argv)
    print "Parsing CarrierSpecConfig.csv done."
