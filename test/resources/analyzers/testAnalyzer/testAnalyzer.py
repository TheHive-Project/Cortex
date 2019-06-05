#!/usr/bin/env python3
# encoding: utf-8

from cortexutils.analyzer import Analyzer
from cortexutils import runner


class TestAnalyzer(Analyzer):

    def artifacts(self, raw):
        return [
            self.build_artifact("ip", "127.0.0.1", tags=["localhost"]),
            self.build_artifact("file", "/etc/issue.net", tlp=3)
        ]

    def summary(self, raw):
        return {"taxonomies": [self.build_taxonomy("info", "test", "data", "test")]}

    def run(self):
        Analyzer.run(self)

        self.report({
            'data': self.get_data(),
            'input': self._input
        })


if __name__ == '__main__':
    runner(TestAnalyzer)
