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

package works.weave.socks.cart.cart;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import works.weave.socks.cart.entities.Cart;

public interface CartDAO {
  void delete(Cart cart);

  Cart save(Cart cart);

  List<Cart> findByCustomerId(String customerId);

  class Fake implements CartDAO {
    private Map<String, Cart> cartStore = new HashMap<>();

    @Override
    public void delete(Cart cart) {
      cartStore.remove(cart.customerId);
    }

    @Override
    public Cart save(Cart cart) {
      return cartStore.put(cart.customerId, cart);
    }

    @Override
    public List<Cart> findByCustomerId(String customerId) {
      Cart cart = cartStore.get(customerId);
      if (cart != null) {
        return Collections.singletonList(cart);
      } else {
        return Collections.emptyList();
      }
    }
  }
}
