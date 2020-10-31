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

package pt.unl.fct.miei.usmanagement.manager.regions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import pt.unl.fct.miei.usmanagement.manager.hosts.Coordinates;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@AllArgsConstructor
@Getter
@ToString
public enum Region implements Serializable {

	NORTH_AMERICA("North America", new Coordinates("North America", 39.787092, -99.754244)),
	SOUTH_AMERICA("South America", new Coordinates("South America", -14.864205, -55.902655)),
	EUROPE("Europe", new Coordinates("Europe", 49.540486, 9.881931)),
	AFRICA("Africa", new Coordinates("Africa", 4.256283, 23.308534)),
	MIDDLE_EAST("Middle East", new Coordinates("Middle East", 31.971059, 46.572714)),
	ASIA("Asia", new Coordinates("Asia", 35.360601, 95.728129)),
	OCEANIA("Oceania", new Coordinates("Oceania", -13.611172, 130.755130));

	private final String name;
	private final Coordinates coordinates;

	public static Region getClosestRegion(Coordinates coordinates) {
		List<Region> regions = Arrays.asList(values());
		regions.sort((oneRegion, anotherRegion) -> {
			double oneDistance = oneRegion.getCoordinates().distanceTo(coordinates);
			double anotherDistance = anotherRegion.getCoordinates().distanceTo(coordinates);
			return Double.compare(oneDistance, anotherDistance);
		});
		return regions.get(0);
	}

	public static Region getRegion(String name) {
		return valueOf(name.toUpperCase().replace(" ", "_"));
	}

	@JsonCreator
	public static Region forValues(@JsonProperty("name") String name, @JsonProperty("coordinates") Coordinates coordinates) {
		for (Region region : Region.values()) {
			if (region.name.equalsIgnoreCase(name) && Objects.equals(region.coordinates, coordinates)) {
				return region;
			}
		}
		return null;
	}

}