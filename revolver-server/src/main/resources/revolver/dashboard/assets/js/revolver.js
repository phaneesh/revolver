/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

/**
 * @author Phaneesh Nagaraja
 */

function checkServiceExists(service_name) {
    return $("#" +service_name +"_service_item").length > 0;
}

function addService(service) {
    var collectionContent = '<li id="' +service.name +'_service_item">';
    collectionContent += '<div class="collapsible-header">';
    collectionContent += '<div class="api-collapsible">';
    if(service.status == "HEALTHY") {
        collectionContent += '<i class="material-icons circle green service-status-icon" id="' +service.name +'_service_status_icon"></i>';
    } else if(service.status == "UNHEALTHY") {
        collectionContent += '<i class="material-icons circle red service-status-icon" id="' +service.name +'_service_status_icon"></i>';
    } else {
        collectionContent += '<i class="material-icons circle grey service-status-icon" id="' +service.name +'_service_status_icon"></i>';
    }
    collectionContent += '<span class="service-status-icon title">' +service.name +'</span>';
    collectionContent += '<p>';
    collectionContent += '<i class="small material-icons">http</i>';
    collectionContent += '<span class="chip" id="' +service.name +'_total_instances_count">Instances: ' +service.instances +'</span>';
    collectionContent += '<span class="chip" id="' +service.name +'_healthy_instances_count">Healthy: ' +service.healthy +'</span>';
    collectionContent += '<span class="chip" id="' +service.name +'_unhealthy_instances_count">Unhealthy: ' +service.unhealthy +'</span>';
    collectionContent += '</p>';
    collectionContent += '</div>';
    collectionContent += '</div>';
    collectionContent += '<div class="collapsible-body light-blue lighten-5">';
    collectionContent += '<ul>';
    collectionContent += addApis(service);
    collectionContent += '</ul>';
    collectionContent += '</div>';
    $("#collection_services").append($(collectionContent));
}

function updateServiceStatus(service) {
    $("#" +service.name +"_service_status_icon").removeClass("green").removeClass("red").removeClass("grey");
    if(service.status == "HEALTHY") {
        $("#" +service.name +"_service_status_icon").addClass("green");
    } else if(service.status == "UNHEALTHY") {
        $("#" +service.name +"_service_status_icon").addClass("red");
    } else {
        $("#" +service.name +"_service_status_icon").addClass("grey");
    }
    $("#" +service.name +"_total_instances_count").text('Instances: ' +service.instances);
    $("#" +service.name +"_healthy_instances_count").text('Healthy: ' +service.healthy);
    $("#" +service.name +"_unhealthy_instances_count").text('Unhealthy: ' +service.unhealthy);
}

function addApis(service) {
    var apiContent = '';
    for(var i=0; i < service.apis.length; i++) {
        apiContent += '<li class="api-collapsible"><a href="#!">';
        for(var j=0; j < service.apis[i].methods.length; j++) {
            if(service.apis[i].secured == true) {
                apiContent += '<span class="chip"><i class="small material-icons">lock</i> ';
            } else {
                apiContent += '<span class="chip"><i class="small material-icons">lock_open</i> ';
            }
            if(service.apis[i].async == true) {
                apiContent += '<i class="small material-icons">swap_vert</i> ';
            }
            apiContent += service.apis[i].methods[j] +'</span>';
        }
        apiContent += '<span> /apis/' +service.name +'/' +service.apis[i].path +'</span>';
        apiContent += '</a></li>';
    }
    apiContent += '</li>';
    return apiContent;
}

function loadData() {
    $.getJSON( "v1/metadata/status", function( data ) {
        $( "#client_id_text" ).text(data.clientId);
        var healthy = $.grep(data.services, function(s) {
            return s.status == "HEALTHY";
        });
        var unhealthy = $.grep(data.services, function(s) {
            return s.status == "UNHEALTHY";
        });
        var unknown = $.grep(data.services, function(s) {
            return s.status == "UNKNOWN";
        });
        console.log("Total Services: " +(healthy.length + unhealthy.length + unknown.length));
        console.log("Healthy Services: " +healthy.length);
        console.log("Unhealthy Services: " +unhealthy.length);
        console.log("Unknown Services: " +unknown.length);
        $("#total_service_count").text((healthy.length + unhealthy.length + unknown.length));
        $("#total_healthy_count").text(healthy.length);
        $("#total_unhealthy_count").text(unhealthy.length);
        $("#total_unknown_count").text(unknown.length);
        $.each(unhealthy, function(i, service) {
           if(!checkServiceExists(service.name)) {
               addService(service);
           } else {
               updateServiceStatus(service);
           }
        });
        $.each(healthy, function(i, service) {
            if(!checkServiceExists(service.name)) {
                addService(service);
            } else {
                updateServiceStatus(service);
            }
        });
        $.each(unknown, function(i, service) {
            if(!checkServiceExists(service.name)) {
                addService(service);
            } else {
                updateServiceStatus(service);
            }
        });
    });
}

// function streamingData() {
//     var jsonStream = new EventSource('http://turbine.stg-mesos.phonepe.int/turbine.stream');
//     jsonStream.onmessage = function (e) {
//         //var message = JSON.parse(e.data);
//         // handle message
//         console.log("Data: " +e.data);
//     };
// }

$( document ).ready(function() {
    $('.dropdown-button').dropdown({
            inDuration: 300,
            outDuration: 225,
            constrain_width: false, // Does not change width of dropdown to that of the activator
            hover: false, // Activate on hover
            gutter: 5, // Spacing from edge
            belowOrigin: false // Displays dropdown below the button
        }
    );
   $("#revolver_dashboard_content").repeat(5000, true, loadData());
});