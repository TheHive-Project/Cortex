'user strict';

export default function(app) {
  app.service('VersionService', function($q, $http) {
    this.get = function() {
      let deferred = $q.defer();

      $http.get('./api/status').then(
        response => {
          deferred.resolve(response);
        },
        rejection => {
          deferred.reject(rejection);
        }
      );
      return deferred.promise;
    };
  });
}
