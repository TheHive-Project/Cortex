'use strict';

export default class SettingsPageController {
  constructor(
    $log,
    $scope,
    $state,
    UserService,
    ModalService,
    NotificationService,
    resizeService,
    clipboard
  ) {
    'ngInject';

    this.$log = $log;
    this.$scope = $scope;
    this.$state = $state;

    this.UserService = UserService;
    this.ModalService = ModalService;
    this.NotificationService = NotificationService;
    this.resizeService = resizeService;
    this.clipboard = clipboard;

    this.basicData = {};
    this.passData = {
      changePassword: false,
      currentPassword: '',
      password: '',
      passwordConfirm: ''
    };
    this.keyData = {};
    this.mfaData = {
      enrolling: false
    };
  }

  $onInit() {
    this.config = this.main.config;
    this.currentUser = this.main.currentUser;

    this.basicData = {
      username: this.currentUser.id,
      name: this.currentUser.name,
      avatar: this.currentUser.avatar
    };

    this.canChangePass =
      this.config.config.capabilities.indexOf('changePassword') !== -1;

    this.canChangeKey =
      this.config.config.capabilities.indexOf('authByKey') !== -1;

    this.canUseMfa = this.config.config.capabilities.indexOf('mfa') !== -1;

    this.$scope.$watch('$ctrl.avatar', value => {
      if (!value) {
        return;
      }

      this.resizeService
        .resizeImage('data:' + value.filetype + ';base64,' + value.base64, {
          height: 100,
          width: 100,
          outputFormat: 'image/jpeg'
        })
        .then(image => {
          this.basicData.avatar = image.replace('data:image/jpeg;base64,', '');
        });
    });
  }

  updateBasicInfo(form) {
    if (!form.$valid) {
      return;
    }

    this.UserService.update(this.currentUser.id, {
      name: this.basicData.name,
      avatar: this.basicData.avatar
    })
      .then(data => {
        this.currentUser.name = data.name;
        this.UserService.updateCache(data._id, data);

        this.NotificationService.log(
          'Your basic information have been successfully updated',
          'success'
        );

        this.$state.reload();
      })
      .catch(err => {
        this.NotificationService.error('SettingsCtrl', err.data, err.status);
      });
  }

  updatePassword(form) {
    if (!form.$valid) {
      return;
    }

    if (
      this.passData.currentPassword &&
      this.passData.password !== '' &&
      this.passData.password === this.passData.passwordConfirm
    ) {
      this.UserService.changePass(
        this.currentUser.id,
        this.passData.currentPassword,
        this.passData.password
      )
        .then(() => {
          this.NotificationService.log(
            'Your password has been successfully updated',
            'success'
          );
          this.$state.reload();
        })
        .catch(response => {
          this.NotificationService.error(
            'SettingsCtrl',
            response.data,
            response.status
          );
        });
    } else {
      this.$state.go('index');
    }
  }
  clearPassword(form, changePassword) {
    if (!changePassword) {
      this.passData.currentPassword = '';
      this.passData.password = '';
      this.passData.passwordConfirm = '';
    }

    form.$setValidity('currentPassword', true);
    form.$setValidity('password', true);
    form.$setValidity('passwordConfirm', true);
    form.$setPristine(true);
  }

  cancel() {
    this.$state.go('index');
  }

  clearAvatar(form) {
    this.basicData.avatar = null;
    form.avatar.$setValidity('maxsize', true);
    form.avatar.$setPristine(true);
  }

  getKey() {
    this.UserService.getKey(this.currentUser.id).then(key => {
      this.keyData.key = key;
    });
  }

  createKey() {
    let modalInstance = this.ModalService.confirm(
      'Renew API Key',
      'Are you sure you want to renew your API Key?',
      {
        flavor: 'danger',
        okText: 'Yes, renew it'
      }
    );

    modalInstance.result
      .then(() => this.UserService.setKey(this.currentUser.id))
      .then(() => {
        delete this.keyData.key;
        this.NotificationService.success(
          'API key has been successfully renewed.'
        );
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to lock the user.');
        }
      });
  }

  copyKey() {
    this.clipboard.copyText(this.keyData.key);
    delete this.keyData.key;
    this.NotificationService.success(
      'API key has been successfully copied to clipboard.'
    );
  }

  /** Step 1: ask the server for a secret. Nothing is persisted until the code is verified. */
  startMfaEnrolment() {
    this.UserService.mfaInit(this.currentUser.id)
      .then(data => {
        this.mfaData = {
          enrolling: true,
          secret: data.secret,
          uri: data.uri,
          qrCode: data.qrCode,
          code: '',
          backupCodes: null
        };
      })
      .catch(err => {
        this.NotificationService.log(
          ((err || {}).data || {}).message ||
            'Unable to start multi-factor enrolment.',
          'error'
        );
      });
  }

  /** Step 2: prove the authenticator is set up. Only then does the server store the secret. */
  confirmMfaEnrolment(form) {
    if (!form.$valid) {
      return;
    }

    this.UserService.mfaSet(this.currentUser.id, this.mfaData.code)
      .then(data => {
        // The codes are shown once and stored hashed, so keep them on screen until dismissed.
        this.mfaData.backupCodes = data.backupCodes;
        this.mfaData.code = '';
        this.NotificationService.success(
          'Multi-factor authentication has been enabled.'
        );
      })
      .catch(err => {
        this.mfaData.code = '';
        this.NotificationService.log(
          ((err || {}).data || {}).message || 'Invalid code, try again.',
          'error'
        );
      });
  }

  cancelMfaEnrolment() {
    this.mfaData = {
      enrolling: false
    };
  }

  /** Dismisses the one-time backup code list and refreshes the cached current user. */
  finishMfaEnrolment() {
    this.mfaData = {
      enrolling: false
    };
    this.$state.reload();
  }

  copyBackupCodes() {
    this.clipboard.copyText(this.mfaData.backupCodes.join('\n'));
    this.NotificationService.success('Backup codes copied to clipboard.');
  }

  disableMfa() {
    let modalInstance = this.ModalService.confirm(
      'Disable two-factor authentication',
      'Are you sure? Your account will be protected by your password alone, and your backup codes will stop working.',
      {
        flavor: 'danger',
        okText: 'Yes, disable it'
      }
    );

    modalInstance.result
      .then(() => this.UserService.mfaUnset(this.currentUser.id))
      .then(() => {
        this.NotificationService.success(
          'Multi-factor authentication has been disabled.'
        );
        this.$state.reload();
      })
      .catch(err => {
        // A string rejection is the modal being dismissed, not a failure.
        if (!_.isString(err)) {
          this.NotificationService.log(
            ((err || {}).data || {}).message ||
              'Unable to disable multi-factor authentication.',
            'error'
          );
        }
      });
  }
}
