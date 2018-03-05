export default class LoginController {
  constructor(
    $log,
    $state,
    $uibModalStack,
    AuthService,
    NotificationService,
    Roles
  ) {
    'ngInject';
    this.$log = $log;
    this.$state = $state;
    this.$uibModalStack = $uibModalStack;
    this.AuthService = AuthService;
    this.NotificationService = NotificationService;
    this.Roles = Roles;
    this.params = {
      bar: 'foo'
    };
  }

  login() {
    this.params.username = angular.lowercase(this.params.username);

    this.AuthService.login(this.params.username, this.params.password)
      .then(user => {
        if (user.roles.indexOf(this.Roles.SUPERADMIN) !== -1) {
          this.$state.go('main.organizations');
        } else {
          this.$state.go('main.jobs');
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
        }
      });
  }

  $onInit() {
    this.$uibModalStack.dismissAll();
  }
}
