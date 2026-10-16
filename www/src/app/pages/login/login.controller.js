import _ from 'lodash';
export default class LoginController {
  constructor(
    $log,
    $scope,
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
    this.$scope = $scope;
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
    // Flipped once the server tells us the account has multi-factor authentication,
    // which reveals the code field and re-submits the same credentials with it.
    this.requireMfa = false;
  }

  login() {
    this.params.username = _.toLower(this.params.username);

    this.AuthService.login(
      this.params.username,
      this.params.password,
      this.requireMfa ? this.params.code : undefined
    )
      .then(() => this.$state.go('index'))
      .catch(err => {
        let type = (err.data || {}).type;

        if (err.status === 520) {
          this.NotificationService.handleError(
            'LoginController',
            err.data,
            err.status
          );
        } else if (type === 'MultiFactorCodeRequired') {
          this.requireMfa = true;
          this.params.code = undefined;
          this.$scope.$broadcast('login-mfa-code');
        } else {
          if (type === 'MultiFactorCodeInvalid') {
            // Keep the code field open so the user can try the next one.
            this.params.code = undefined;
            this.$scope.$broadcast('login-mfa-code');
          } else {
            // Anything else (bad password, locked account) sends them back to step one.
            this.requireMfa = false;
          }
          this.NotificationService.log((err.data || {}).message, 'error');
        }
      });
  }

  cancelMfa() {
    this.requireMfa = false;
    this.params.code = undefined;
    this.params.password = undefined;
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