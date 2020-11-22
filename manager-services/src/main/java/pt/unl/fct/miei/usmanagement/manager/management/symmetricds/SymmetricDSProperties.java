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

package pt.unl.fct.miei.usmanagement.manager.management.symmetricds;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties("symmetricds")
public class SymmetricDSProperties {

	private final Master master;
	private final Worker worker;

	public SymmetricDSProperties() {
		this.master = new Master();
		this.worker = new Worker();
	}

	@Getter
	@Setter
	public static final class Master {

		private final Tables tables;

		private Master() {
			this.tables = new Tables();
		}

		@Getter
		@Setter
		public static final class Tables {

			private final Exclude exclude;
			private final Include include;

			private Tables() {
				this.exclude = new Exclude();
				this.include = new Include();
			}

			@Getter
			@Setter
			public static final class Exclude {

				private List<String> startsWith;
				private List<String> endsWith;
				private List<String> contains;

			}

			@Getter
			@Setter
			public static final class Include {

				private List<String> startsWith;
				private List<String> endsWith;
				private List<String> contains;

			}

		}

	}

	@Getter
	@Setter
	public static final class Worker {

		private final Tables tables;

		private Worker() {
			this.tables = new Tables();
		}

		@Getter
		@Setter
		public static final class Tables {

			private final Exclude exclude;
			private final Include include;

			private Tables() {
				this.exclude = new Exclude();
				this.include = new Include();
			}

			@Getter
			@Setter
			public static final class Exclude {

				private List<String> startsWith;
				private List<String> endsWith;
				private List<String> contains;

			}

			@Getter
			@Setter
			public static final class Include {

				private List<String> startsWith;
				private List<String> endsWith;
				private List<String> contains;

			}

		}

	}


}
