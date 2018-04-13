'use strict';

import UserEditController from '../user-dialog/user.edit.controller';
import editModalTpl from '../user-dialog/user.edit.modal.html';

export default class UsersListController {
  constructor(
    $log,
    $scope,
    $uibModal,
    OrganizationService,
    UserService,
    NotificationService,
    ModalService,
    clipboard
  ) {
    'ngInject';

    this.$log = $log;
    this.$scope = $scope;
    this.$uibModal = $uibModal;
    this.OrganizationService = OrganizationService;
    this.UserService = UserService;
    this.NotificationService = NotificationService;
    this.ModalService = ModalService;
    this.clipboard = clipboard;

    this.userKeyCache = {};
    this.showPwdForm = {};
  }

  $onInit() {
    this.canSetPass =
      this.main.config.config.capabilities.indexOf('setPassword') !== -1;
  }

  reload() {
    this.onReload();
  }

  showPassword(user, visible) {
    this.showPwdForm[user.id] = visible;
    if (visible) {
      this.$scope.$broadcast(`user-showPassword-${user.id}`);
    }
  }

  getKey(user) {
    this.UserService.getKey(user.id).then(key => {
      this.userKeyCache[user.id] = key;
    });
  }

  createKey(user) {
    this.UserService.setKey(user.id)
      .then(() => {
        delete this.userKeyCache[user.id];
        this.reload();
        this.NotificationService.success(
          `API key of user ${user.id} has been successfully created.`
        );
      })
      .catch(response => {
        this.NotificationService.error(
          'AdminUsersCtrl',
          response.data,
          response.status
        );
      });
  }

  revokeKey(user) {
    this.UserService.revokeKey(user.id)
      .then(() => {
        delete this.userKeyCache[user.id];
        this.reload();
        this.NotificationService.success(
          `API key of user ${user.id} has been successfully revoked.`
        );
      })
      .catch(response => {
        this.NotificationService.error(
          'OrganizationUsersListController',
          response.data,
          response.status
        );
      });
  }

  copyKey(user) {
    this.clipboard.copyText(this.userKeyCache[user.id]);
    delete this.userKeyCache[user.id];
    this.NotificationService.success(
      `API key of user ${user.id} has been successfully copied to clipboard.`
    );
  }

  setPassword(user, password) {
    if (!this.canSetPass) {
      return;
    }

    this.UserService.setPass(user.id, password)
      .then(() => {
        this.NotificationService.success(
          `Password of user ${user.id} has been successfully updated.`
        );
      })
      .catch(response => {
        this.NotificationService.error(
          'OrganizationUsersListController',
          response.data,
          response.status
        );
      });
  }

  openModal(mode, user) {
    let modal = this.$uibModal.open({
      animation: true,
      controller: UserEditController,
      controllerAs: '$modal',
      templateUrl: editModalTpl,
      size: 'lg',
      resolve: {
        organization: () => this.organization,
        user: () => angular.copy(user),
        mode: () => mode
      }
    });

    modal.result
      .then(() => {
        this.reload();
        mode;
        this.NotificationService.success(
          `User ${mode === 'edit' ? 'updated' : 'created'} successfully`
        );
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to update the user.');
        }
      });
  }

  lockUser(id) {
    if (id === this.main.currentUser.id) {
      return;
    }

    let modalInstance = this.ModalService.confirm(
      'Lock user',
      'Are you sure you want to lock this user? They will no longer be able to have access to Cortex.',
      {
        flavor: 'danger',
        okText: 'Yes, lock the user'
      }
    );

    modalInstance.result
      .then(() => this.UserService.update(id, { status: 'Locked' }))
      .then(() => {
        this.reload();
        this.NotificationService.success('User locked successfully');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to lock the user.');
        }
      });
  }

  unlockUser(id) {
    if (id === this.main.currentUser.id) {
      return;
    }

    let modalInstance = this.ModalService.confirm(
      'Unlock user',
      'Are you sure you want to unlock this user? They will be able to have access to Cortex.',
      {
        flavor: 'danger',
        okText: 'Yes, unlock the user'
      }
    );

    modalInstance.result
      .then(() => this.UserService.update(id, { status: 'Ok' }))
      .then(
        /*response*/
        () => {
          this.reload();
          this.NotificationService.success('User unlocked successfully');
        }
      )
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to unlock the user.');
        }
      });
  }
}
