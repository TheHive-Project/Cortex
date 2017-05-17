#!/usr/bin/env python3
# -*- coding: utf-8 -*-


import sys
import os
import getopt
import json

import misp_modules

"""
git clone https://github.com/MISP/misp-modules.git
cd misp_modules
pip3 -r REQUIREMENTS
pip3 install .
"""


def usage():
    print(__file__ + " --list")
    print(__file__ + " --info <misp-module>")
    print(__file__ + " --run <misp-module>")


def run(argv):
    mhandlers, modules = misp_modules.load_package_modules()
    try:
        opts, args = getopt.getopt(argv, 'lh:i:r:', ["list", "help", "info=", "run="])
    except getopt.GetoptError as err:
        usage()
        print(str(err))
        sys.exit(2)

    for opt, arg in opts:

        # TODO: check if module exist else exit
        if opt in ('-h', '--help'):
            usage()
            sys.exit()

        elif opt in ('-l', '--list'):
            modules = [m for m in modules if mhandlers['type:' + m] == "expansion"]
            print(json.dumps(modules))
            sys.exit(0)

        elif opt in ('-r', '--run'):
            module_name = arg
            try:
                data = json.load(sys.stdin)
                print(json.dumps(mhandlers[module_name].handler(json.dumps(data))))
            except:
                error = {'error': sys.exc_info()[1].args[0]}
                print(json.dumps(error))
            sys.exit(0)

        elif opt in ('-i', '--info'):
            module_name = arg

            try:
                config = mhandlers[module_name].moduleconfig
            except AttributeError:
                config = []
            print(json.dumps({
                'name': module_name,
                'mispattributes': mhandlers[module_name].mispattributes,
                'moduleinfo': mhandlers[module_name].moduleinfo,
                'config': config
            }))


if __name__ == '__main__':
    if len(sys.argv[1:]) > 0:
        run(sys.argv[1:])
    else:
        usage()
        sys.exit(2)
