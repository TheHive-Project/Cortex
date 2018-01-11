'use strict';

import _ from 'lodash';

export default class OrganizationPageController {
  constructor($log, OrganizationService) {
    'ngInject';

    this.$log = $log;
    this.OrganizationService = OrganizationService;
  }

  $onInit() {
    this.definitionsIds = _.difference(
      _.keys(this.analyzerDefinitions),
      _.map(this.analyzers, 'analyzerDefinitionId')
    ).sort();
  }

  enable(analyzerId) {
    this.OrganizationService.enableAnalyzer(this.organization.id, analyzerId, {
      name: analyzerId
    }).then(response => {
      this.$log.log(`Analyzer ${analyzerId} enabled`, response);
      this.reload();
      this.$onInit();
    });
  }

  reload() {
    this.OrganizationService.analyzers(this.organization.id).then(
      analyzers => (this.analyzers = analyzers)
    );
  }
}
