import tpl from './index.html';

class AoiDrawToolbarController {
    constructor($rootScope, $scope, $state, $log, $timeout) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.setComponentStyle();
    }

    setComponentStyle() {
        this.eleStyle = {
            top: `${-angular.element(document.querySelector('.navbar'))[0].offsetHeight}px`
        };
    }
}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AoiDrawToolbarController.name
};

export default angular
    .module('components.projects.aoiDrawToolbar', [])
    .controller(AoiDrawToolbarController.name, AoiDrawToolbarController)
    .component('rfAoiDrawToolbar', component)
    .name;
