/*
 * MIT License
 *
 * Copyright (c) 2020 usmanager
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

import React from 'react';
import { Link } from 'react-router-dom';
import CardItem from '../shared/CardItem';
import ServiceRules from './ServiceRules';
import MainLayout from '../shared/MainLayout';
import {getData} from "../../utils/data";

export default class ServicesRulesList extends React.Component {
  constructor (props) {
    super(props);
    this.state = { services: [], loading: false };
  }

  componentDidMount = () => {
    this.loadServices();
  };

  loadServices = () => {
    this.setState({ loading: true });
    getData(
        'http://localhost/services',
        data => {
          this.setState({ services: data, loading: false });
        });
  };

  /* <MainLayout title={{title: 'Services rules'}}>*/
  render = () => (
      <MainLayout>
        {this.state.services && this.state.services.map(service => (
            <div key={service.id} className='row'>
              <div className='col s12'>
                <div className='card'>
                  <div className='card-content'>
                    <div className="right-align">
                      <div className="row">
                        <div className="col s12">
                          <Link className="waves-effect waves-light btn-small"
                                to={'/rules/services/' + service.id}>
                            View details
                          </Link>
                        </div>
                      </div>
                    </div>
                    <CardItem label='Service' value={service.serviceName}/>
                    <ServiceRules service={service}/>
                  </div>
                </div>
              </div>
            </div>
        ))}
      </MainLayout>
  );
}
