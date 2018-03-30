'use strict';

export default class OrganizationModalController {
  constructor($log, $uibModalInstance, organization, mode) {
    'ngInject';

    this.$log = $log;
    this.organization = organization;
    this.$uibModalInstance = $uibModalInstance;
    this.mode = mode;

    this.initForm(organization);
  }

  initForm(organization) {
    this.formData = _.defaults(
      _.pick(organization || {}, 'id', 'name', 'description', 'status'),
      {
        name: null,
        description: null,
        status: 'Active'
      }
    );
  }

  ok() {
    this.$uibModalInstance.close(this.formData);
  }

  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }
}
