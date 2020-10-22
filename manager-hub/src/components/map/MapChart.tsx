/*
 * MIT License
 *
 * Copyright (c) 2020 manager
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

import {ComposableMap, Geographies, Geography, Marker, Point, ZoomableGroup} from "react-simple-maps";
import React, {createRef, memo} from "react";
import * as d3Geo from "d3-geo";
import {IMarker} from "./Marker";

const {geoPath} = d3Geo

type Props = {
    setTooltipContent: (tooltip: string) => void;
    onClick?: (marker: IMarker) => void;
    markers?: {coordinates: Point, marker: JSX.Element}[];
    hover?: boolean;
    clickHighlight?: boolean;
    zoomable?: boolean;
    position?: { coordinates: Point, zoom: number },
    center?: boolean;
    onZoom?: (position: { coordinates: Point, zoom: number }) => void;
}

type State = {
    scale: number;
    position: { coordinates: Point, zoom: number };
}

class MapChart extends React.Component<Props, State> {

    private MAP_MAX_WIDTH = window.innerWidth;
    private MAP_MAX_HEIGHT = window.innerHeight - 125;
    private map = createRef<HTMLDivElement>();

    constructor(props: Props) {
        super(props);
        this.state = { scale: 1.0, position: { coordinates: [0, 0], zoom: 1 } };
    }

    public componentDidMount() {
        if (global.window) {
            this.handleResize()
            global.window.addEventListener('resize', this.handleResize);
        }
        this.setState({scale: 1.0});
    }

    componentDidUpdate(prevProps: Readonly<Props>, prevState: Readonly<State>, snapshot?: any) {
        this.calculateScale();
        if (this.props.center !== undefined && prevProps.center !== this.props.center) {
            this.setState({position: {coordinates: [0, 0], zoom: 1}});
        }
        if (prevProps.zoomable !== this.props.zoomable) {
            this.setState({position: {coordinates: this.props.position?.coordinates || [0, 0], zoom: 1}});
        }
    }

    public componentWillUnmount() {
        if (global.window) {
            global.window.removeEventListener('resize', this.handleResize);
        }
    }

    private handleResize = () =>
        this.calculateScale();

    private calculateScale = () => {
        const map = this.map.current;
        if (map) {
            const {width} = map.getBoundingClientRect();
            const newScale = width / this.MAP_MAX_WIDTH;
            if (newScale !== this.state.scale) {
                this.setState({scale: newScale});
            }
        }
    }

    private onGeographyClick = (geography: any, projection: any) => (evt: any) => {
        const gp = geoPath().projection(projection);
        const dim = evt.target.getBoundingClientRect();
        const cx = evt.clientX - dim.left;
        const cy = evt.clientY - dim.top;
        const [orgX, orgY] = gp.bounds(geography)[0];
        const {scale} = this.state;
        const coordinates = projection.invert([orgX + cx / scale, orgY + cy / scale]);
        this.props.onClick?.({label: geography.properties.NAME, title: geography.properties.NAME, longitude: coordinates[0], latitude: coordinates[1]});
    }

    private handleMoveEnd = (position: { coordinates: Point, zoom: number }) => {
        this.setState({position: position});
        this.props.onZoom?.(position);
    }

    public render() {
        const {setTooltipContent, hover, clickHighlight, markers, zoomable} = this.props;
        const {position} = this.state;
        const geoUrl = "/resources/world-110m.json";
        return (
            <div style={{width: '100%', maxWidth: this.MAP_MAX_WIDTH, margin: '0 auto'}} ref={this.map}>
                <ComposableMap data-tip="" projectionConfig={{scale: 315, rotate: [-11, 0, 0]}}
                               width={this.MAP_MAX_WIDTH} height={this.MAP_MAX_HEIGHT}
                               style={{width: '100%', height: 'auto'}}>
                    <ZoomableGroup zoom={position.zoom} maxZoom={!zoomable ? 1 : 5} center={position.coordinates} onMoveEnd={this.handleMoveEnd}>
                        <Geographies geography={geoUrl}>
                            {({geographies, projection}) =>
                                geographies.map(geo => (
                                    <Geography
                                        key={geo.rsmKey}
                                        geography={geo}
                                        onClick={this.onGeographyClick(geo, projection)}
                                        onMouseEnter={() => {
                                            const {NAME} = geo.properties;
                                            setTooltipContent(`${NAME}`);
                                        }}
                                        onMouseLeave={() => {
                                            setTooltipContent("");
                                        }}
                                        style={{
                                            default: {
                                                fill: "#D6D6DA",
                                                outline: "none"
                                            },
                                            hover: {
                                                fill: hover ? "#F53" : "#D6D6DA",
                                                outline: "none"
                                            },
                                            pressed: {
                                                fill: clickHighlight ? "#E42" : "#D6D6DA",
                                                outline: "none"
                                            }
                                        }}
                                    />
                                ))
                            }
                        </Geographies>
                        {markers?.map((marker, index) =>
                            <Marker key={index} coordinates={marker.coordinates}>
                                {marker.marker}
                            </Marker>)}
                    </ZoomableGroup>
                </ComposableMap>
            </div>
        );
    }

}

export default memo(MapChart);