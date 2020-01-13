/*
 * MIT License
 *
 * Copyright (c) 2020 micro-manager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import $ from 'jquery';
import M from 'materialize-css';

const displayProgressBar = () =>
  $('#loader-placeholder').html('<div class="progress"><div class="indeterminate"></div></div>');

const hideProgressBar = (delay) =>
// TODO remove delay when server and client are on different machines
  setTimeout(() => $('#loader-placeholder').html(''), delay);

class Utils {
  ajaxGet (url, successFunction) {
    displayProgressBar();
    fetch(`http://${url}`, {
      method: 'GET'
    }).then(response => {
      if (response.ok) {
        return response.json();
      }
      // throw new Error(response.statusText);
      M.toast({ html: '<div>Error: ' + response.statusText + '; Code: ' + response.status + '</div>' });
    }).then(json => {
      successFunction(json);
      hideProgressBar(200);
    });
    /*      success: (data) => {
        console.log(data);
        // successFunction(data);
        hideProgressBar(200);
      },
      error: (xhr, status, err) => {
        M.toast({ html: '<div>Error: ' + xhr.statusText + '; Code: ' + xhr.status + '</div>' });
        hideProgressBar(200);
      }
    }); */
  };

  convertFormToJson (formId) {
    const form = $('#' + formId).serializeArray();
    const formObject = {};
    $.each(form, (i, v) => {
      const valueTest = v.value.replace(',', '.');
      const number = Number(valueTest);
      if (isNaN(number) || v.value === '') {
        formObject[v.name] = v.value;
      } else {
        formObject[v.name] = number;
      }
    }
    );
    return JSON.stringify(formObject);
  };

  formSubmit (formUrl, formMethod, formData, successFunction) {
    $.ajax({
      url: formUrl,
      type: formMethod,
      data: formData,
      dataType: 'json',
      contentType: 'application/json',
      cache: false,
      beforeSend: () =>
        displayProgressBar(),
      success: (data) => {
        hideProgressBar(200);
        successFunction(data);
      },
      error: (xhr, status, err) => {
        hideProgressBar(200);
        M.toast({ html: '<div>Error: ' + xhr.statusText + '; Code: ' + xhr.status + '</div>' });
      }
    });
    return false;
  };
}

export default (new Utils());