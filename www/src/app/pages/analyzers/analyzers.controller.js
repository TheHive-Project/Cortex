'use strict';

import _ from 'lodash/core';

export default class AnalyzersController {
  constructor($log, $state, $uibModal, AnalyzerService, NotificationService) {
    'ngInject';
    this.$log = $log;
    this.$state = $state;
    this.$uibModal = $uibModal;
    this.AnalyzerService = AnalyzerService;
    this.NotificationService = NotificationService;

    this.search = {
      description: '',
      dataTypeList: ''
    };

    this.datatypes = AnalyzerService.getTypes();

    this.$log.log(this.datatypes);
  }

  $onInit() {
    this.$log.debug('Called from analyzers controller');
  }

  filterByType(type) {
    if (this.search.dataTypeList === type) {
      this.search.dataTypeList = '';
    } else {
      this.search.dataTypeList = type;
    }
  }

  run(analyzer, dataType) {
    analyzer.active = true;

    this.AnalyzerService.openRunModal([analyzer], {
      dataType: dataType
    })
      .then(responses => {
        this.$state.go('main.jobs');

        responses.forEach(resp => {
          this.NotificationService.success(
            `${resp.data.analyzerName} started successfully on ${resp.data
              .data || resp.data.attributes.filename}`
          );
        });
      })
      .catch(err => {
        this.$log.log(err);
        if (!_.isString(err)) {
          this.NotificationService.error(
            'An error occurred: ' + err.statusText ||
              'An unexpected error occurred'
          );
        }
      });
  }
}
