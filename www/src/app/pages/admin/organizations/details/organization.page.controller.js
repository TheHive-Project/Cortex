'use strict';

import _ from 'lodash';

export default class OrganizationPageController {
  constructor(
    $log,
    $stateParams,
    AnalyzerService,
    OrganizationService,
    AuthService
  ) {
    'ngInject';

    this.$log = $log;
    this.orgId = $stateParams.id;
    this.AnalyzerService = AnalyzerService;
    this.OrganizationService = OrganizationService;
    this.AuthService = AuthService;
  }
}
