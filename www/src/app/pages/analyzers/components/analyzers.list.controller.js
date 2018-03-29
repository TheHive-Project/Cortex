'use strict';

export default class AnalyzerListController {
  constructor($state, AnalyzerService, NotificationService) {
    'ngInject';

    this.$state = $state;
    this.AnalyzerService = AnalyzerService;
    this.NotificationService = NotificationService;
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
              .data || resp.data.attachment.name}`
          );
        });
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error(
            err.data.message ||
              `An error occurred: ${err.statusText}` ||
              'An unexpected error occurred'
          );
        }
      });
  }
}
