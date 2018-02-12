'use strict';

import _ from 'lodash';

export default class UserEditController {
  constructor(
    $log,
    $uibModalInstance,
    UserService,
    OrganizationService,
    NotificationService,
    organization,
    user,
    mode
  ) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;

    this.UserService = UserService;
    this.NotificationService = NotificationService;
    this.OrganizationService = OrganizationService;
    this.organization = organization;
    this.user = user;
    this.mode = mode;

    const orgId = (this.organization || {}).id;

    this.formData = _.defaults(
      _.pick(this.user, 'id', 'name', 'roles', 'organization'),
      {
        id: null,
        name: null,
        roles: [],
        organization: orgId
      }
    );

    this.isEdit = this.mode === 'edit';
    this.orgId = orgId;
    this.organizations = this.organization ? [this.organization] : [];
  }

  $onInit() {
    if (_.isEmpty(this.organizations)) {
      this.OrganizationService.list().then(orgs => (this.organizations = orgs));
    }
  }

  onSuccess(data) {
    this.$uibModalInstance.close(data);
  }

  onFailure(response) {
    this.$uibModalInstance.dismiss(response);
  }

  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }

  resetLoginError(form) {
    form.login.$setValidity('exists', true);
  }

  saveUser(form) {
    if (!form.$valid) {
      return;
    }

    let postData = _.pick(this.formData, 'name', 'roles', 'organization');
    let promise;

    if (this.user.id) {
      promise = this.UserService.update(this.user.id, postData);
    } else {
      postData.login = angular.lowercase(this.formData.id);
      promise = this.UserService.save(postData);
    }

    return promise
      .then(response => this.onSuccess(response.data))
      .catch(rejection => {
        const { data: { type } } = rejection;
        if (type === 'ConflictError') {
          form.login.$setValidity('exists', false);
        } else {
          this.onFailure(rejection.data);
        }
      });
  }
}
