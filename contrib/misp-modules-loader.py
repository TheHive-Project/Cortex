#!/usr/bin/env python
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


def run(argv):

    mhandlers, modules = misp_modules.load_package_modules()
    try:
        opts, args = getopt.getopt(argv, 'lh:i:r:', ["list", "help", "info=","run="])
    except getopt.GetoptError as err:
        print(__file__ + " --info <misp-module>")
        print(__file__ + "  --run <misp-module>")
        print(str(err))
        sys.exit(2)

    module = None
    path = None
    for opt,arg in opts:

        # TODO: check if module exist else exit
        if opt in ('-h', '--help'):
            print(__file__ + " --info <misp-module>")
            print(__file__ + " --run <misp-module>")
            sys.exit()

        elif opt in ('-l', '--list'):
                print(modules)
                sys.exit(0)

        elif opt in ('-r', '--run'):
                module = arg
                data = json.load(sys.stdin)
                print(json.dumps(mhandlers[module].handler(json.dumps(data))))
                sys.exit(0)

        elif opt in ('-i','--info'):
            module = arg

            print(({'name': module, 'mispattributes': mhandlers[module].mispattributes,
                    'moduleinfo':mhandlers[module].moduleinfo}))




if __name__ == '__main__':
    if len(sys.argv[1:]) > 0:
        run(sys.argv[1:])
    else:
        print(__file__ + " --info <misp-module>")
        print(__file__ + " --run <misp-module>")
        sys.exit(2)
