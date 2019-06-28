'use strict';

import _ from 'lodash';

export default class UserEditController {
  constructor(
    $log,
    $uibModalInstance,
    Roles,
    AuthService,
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

    this.Roles = Roles;
    this.AuthService = AuthService;
    this.UserService = UserService;
    this.NotificationService = NotificationService;
    this.OrganizationService = OrganizationService;
    this.organization = organization;
    this.user = user;
    this.mode = mode;

    this.isEdit = this.mode === 'edit';

    const orgId = (this.organization || {}).id || null;
    this.orgId = orgId;

    this.formData = _.defaults(
      _.pick(this.user, 'id', 'name', 'roles', 'organization'), {
        id: null,
        name: null,
        roles: orgId && orgId === 'cortex' ? [Roles.SUPERADMIN] : [Roles.READ, Roles.ANALYZE],
        organization: orgId
      }
    );

    this.organizations = this.organization ? [this.organization] : [];
    this.rolesList = this.getRolesList(orgId);
  }

  $onInit() {
    if (_.isEmpty(this.organizations)) {
      this.OrganizationService.list().then(orgs => (this.organizations = orgs));
    }
  }

  getRolesList(orgId) {
    return orgId && orgId === 'cortex' ? [
      [this.Roles.SUPERADMIN]
    ] : [
      [this.Roles.READ],
      [this.Roles.READ, this.Roles.ANALYZE],
      [this.Roles.READ, this.Roles.ANALYZE, this.Roles.ORGADMIN]
    ];
  }

  onOrgChange() {
    this.$log.log(this.formData.organization);

    this.rolesList = this.getRolesList(this.formData.organization);

    this.formData.roles =
      this.formData.organization === 'cortex' ? [this.Roles.SUPERADMIN] : [this.Roles.READ, this.Roles.ANALYZE];
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

    let postData;
    let promise;

    if (
      this.AuthService.currentUser.roles.indexOf(this.Roles.SUPERADMIN) !== -1
    ) {
      postData = _.pick(this.formData, 'name', 'roles', 'organization');
    } else {
      postData = _.pick(this.formData, 'name', 'roles');
    }

    if (this.user.id) {
      promise = this.UserService.update(this.user.id, postData);
    } else {
      postData.login = _.toLower(this.formData.id);
      promise = this.UserService.save(postData);
    }

    return promise
      .then(response => this.onSuccess(response.data))
      .catch(rejection => {
        const {
          data: {
            type
          }
        } = rejection;
        if (type === 'ConflictError') {
          form.login.$setValidity('exists', false);
        } else {
          this.onFailure(rejection.data);
        }
      });
  }
}