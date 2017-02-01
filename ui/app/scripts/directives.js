(function() {
    'use strict';
    angular.module('cortex')
        .directive('jobDetails', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/directives.jobdetails.html',
                scope: {
                    job: '='
                }
            };
        })
        .directive('fixedHeight', function($window, $timeout){
            return {
                restrict: 'A',
                link: function(scope, elem, attr) {

                    $timeout(function() {
                        var windowHeight = $(window).height();
                        var footerHeight = $('.main-footer').outerHeight();
                        var headerHeight = $('.main-header').height();
                        
                        elem.css('min-height', windowHeight - headerHeight - footerHeight);
                    }, 500);
                    
                    angular.element($window).bind('resize', function(){
                        var windowHeight = $(window).height();
                        var footerHeight = $('.main-footer').outerHeight();
                        var headerHeight = $('.main-header').height();
                        
                        elem.css('min-height', windowHeight - headerHeight - footerHeight);
                    });
                    
                }
            }
        })
        .directive('fileChooser', function() {
            return {
                'restrict': 'A',
                'link': function(scope, element) {
                    var dropzone;
                    element.addClass('dropzone');
                    var template = element[0].innerHTML;
                    $(element[0].children[0]).remove();
                    // create a Dropzone for the element with the given options
                    dropzone = new Dropzone(element[0], {
                        // 'clickable' : '.dz-clickable',
                        'url': 'dummy',
                        'autoProcessQueue': false,
                        'maxFiles': 1,
                        // 'addRemoveLinks' : true,
                        'createImageThumbnails': (angular.isString(scope.preview)) ? (scope.preview === 'true') : true,
                        'acceptedFiles': (angular.isString(scope.accept)) ? scope.accept : undefined,
                        'previewTemplate': template
                    });

                    dropzone.on('addedfile', function(file) {
                        scope.$apply(function() {
                            scope.filemodel = file;
                        });
                    });
                    dropzone.on('removedfile', function() {
                        setTimeout(function() {
                            scope.$apply(function() {
                                delete scope.filemodel;
                                // scope.filemodel = undefined;
                            });
                        }, 0);
                    });
                    dropzone.on('maxfilesexceeded', function(file) {
                        this.removeFile(file);
                    });
                    if (angular.isDefined(scope.control)) {
                        scope.control.removeAllFiles = function() {
                            dropzone.removeAllFiles();
                        };
                    }
                },
                'templateUrl': 'views/directives.dropzone.html',
                'scope': {
                    'filemodel': '=',
                    'control': '=',
                    'preview': '@?',
                    'accept': '@?'
                }
            };
        });

})();
