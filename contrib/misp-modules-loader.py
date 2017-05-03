#!/usr/bin/env python
# -*- coding: utf-8 -*-


import sys
import os
import getopt
import json


def mod(path):
    dirname = os.path.dirname(path)
    name = os.path.basename(path).split('.')[0]
    return [dirname, name]


def run(argv):
    try:
        opts, args = getopt.getopt(argv, 'hp:i:r:', ["help", "info=","run="])
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

        elif opt in ('-r', '--run'):
            path = arg
            if path:
                sys.path.append(mod(path)[0])
                m = __import__(mod(path)[1])

                data = json.load(sys.stdin)
                print(json.dumps(m.handler(json.dumps(data))))
                sys.exit(0)

            else:
                print("add module path")

        elif opt in ('-i','--info'):
            path = arg
            if path:
                sys.path.append(mod(path)[0])
                m = __import__(mod(path)[1])
                print(({'name': module, 'mispattributes': m.mispattributes,
                        'moduleinfo':m.moduleinfo}))




if __name__ == '__main__':
    if len(sys.argv[1:]) > 0:
        run(sys.argv[1:])
    else:
        print(__file__ + " --info <misp-module>")
        print(__file__ + " --run <misp-module>")
        sys.exit(2)
