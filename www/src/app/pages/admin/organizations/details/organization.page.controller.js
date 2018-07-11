'use strict';

export default class OrganizationPageController {
  constructor(
    $log,
    $stateParams,
    AnalyzerService,
    ResponderService,
    OrganizationService,
    AuthService
  ) {
    'ngInject';

    this.$log = $log;
    this.orgId = $stateParams.id;
    this.AnalyzerService = AnalyzerService;
    this.ResponderService = ResponderService;
    this.OrganizationService = OrganizationService;
    this.AuthService = AuthService;
  }
}