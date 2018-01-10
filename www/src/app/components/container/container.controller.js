'use strict';

export default class ContainerController {
  constructor($log, VersionService) {
    'ngInject';
    this.$log = $log;
    this.VersionService = VersionService;

    this.config = {};
  }

  $onInit() {
    this.VersionService.get().then(response => {
      this.config = response.data;
    });

    this.$log.log('Hello from the container component controller!');
  }
}
