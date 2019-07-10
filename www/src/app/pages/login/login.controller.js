import _ from 'lodash';
export default class LoginController {
  constructor(
    $log,
    $state,
    $uibModalStack,
    $location,
    $stateParams,
    AuthService,
    NotificationService,
    Roles,
    UrlParser
  ) {
    'ngInject';
    this.$log = $log;
    this.$state = $state;
    this.$uibModalStack = $uibModalStack;
    this.$location = $location;
    this.$stateParams = $stateParams;
    this.AuthService = AuthService;
    this.NotificationService = NotificationService;
    this.Roles = Roles;
    this.UrlParser = UrlParser;
    this.params = {
      bar: 'foo'
    };
  }

  login() {
    this.params.username = _.toLower(this.params.username);

    this.AuthService.login(this.params.username, this.params.password)
      .then(() => this.$state.go('index'))
      .catch(err => {
        if (err.status === 520) {
          this.NotificationService.handleError(
            'LoginController',
            err.data,
            err.status
          );
        } else {
          this.NotificationService.log(err.data.message, 'error');
        }
      });
  }

  ssoLogin(code) {
    this.AuthService.ssoLogin(code)
      .then(response => {
        let redirectLocation = response.headers().location;
        if (angular.isDefined(redirectLocation)) {
          window.location = redirectLocation;
        } else {
          this.$state.go('index');
        }
      })
      .catch(err => {
        if (err.status === 520) {
          this.NotificationService.handleError(
            'LoginController',
            err.data,
            err.status
          );
        } else {
          this.NotificationService.log(err.data.message, 'error');
          this.$location.url(this.$location.path());
        }
      });
  }

  ssoEnabled() {
    return this.config.config.authType.indexOf('oauth2') !== -1;
  }

  $onInit() {
    this.$uibModalStack.dismissAll();

    let code = (this.UrlParser('?') || {}).code;
    if (angular.isDefined(code) || this.$stateParams.autoLogin) {
      this.ssoLogin(code);
    }
  }
}