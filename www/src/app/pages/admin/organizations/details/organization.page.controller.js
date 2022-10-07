'use strict';

export default class OrganizationPageController {
  constructor(
    $log,
    $stateParams,
    $scope,
    AnalyzerService,
    ResponderService,
    OrganizationService,
    AuthService,
    AlertService
  ) {
    'ngInject';

    this.$log = $log;
    this.$scope = $scope;
    this.orgId = $stateParams.id;
    this.AnalyzerService = AnalyzerService;
    this.ResponderService = ResponderService;
    this.OrganizationService = OrganizationService;
    this.AuthService = AuthService;
    this.AlertService = AlertService;
  }


  $onInit() {
    this.AlertService.startUpdate();

    this.$scope.$on('$destroy', () => {
      this.AlertService.stopUpdte();
    });
  }
}