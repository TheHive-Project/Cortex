'use strict';

export default function(app) {
  app.directive('compareTo', compareTo);

  function compareTo() {
    'ngInject';

    return {
      require: 'ngModel',
      scope: {
        otherModelValue: '=compareTo'
      },
      link: linkFn
    };

    function linkFn(scope, element, attributes, ngModel) {
      ngModel.$validators.compareTo = function(modelValue) {
        return modelValue === scope.otherModelValue;
      };

      scope.$watch('otherModelValue', function() {
        ngModel.$validate();
      });
    }
  }
}
