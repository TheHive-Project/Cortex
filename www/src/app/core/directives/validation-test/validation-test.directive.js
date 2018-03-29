'use strict';

export default function (app) {

    app.directive('validationTest', validationTestDirective);

    function validationTestDirective () {
        'ngInject';

        return {
            restrict: 'A',
            link: linkFn,
            require: 'ngModel'
        };

        function linkFn (scope, elem, attrs, ngModelCtrl) {
            scope.$watch(attrs.ngModel, newVal => {
                if (newVal === 'test') {
                    ngModelCtrl.$setValidity('test', true);
                } else {
                    ngModelCtrl.$setValidity('test', false);
                }
            });
        }
    }
}