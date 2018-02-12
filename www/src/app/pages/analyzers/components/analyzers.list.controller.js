'use strict';

export default class AnalyzerListController {
  constructor($state, AnalyzerService, NotificationService) {
    'ngInject';

    this.$state = $state;
    this.AnalyzerService = AnalyzerService;
    this.NotificationService = NotificationService;
  }

  run(analyzer, dataType) {
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
        if (!_.isString(err)) {
          this.NotificationService.error(
            'An error occurred: ' + err.statusText ||
              'An unexpected error occurred'
          );
        }
      });
  }
}
