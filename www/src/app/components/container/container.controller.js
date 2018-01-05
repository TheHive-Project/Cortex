'use strict';

export default class ContainerController {
  constructor($log) {
    'ngInject';
    this.$log = $log;
  }

  $onInit() {
    this.$log.log('Hello from the container component controller!');
  }
}

/*
function(VersionSrv) {
        var self = this;
        self.config = {};

        VersionSrv.get()
            .then(function(response) {
                self.config = response.data;
            });
    }
    */
