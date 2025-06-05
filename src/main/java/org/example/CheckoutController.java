package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/*
desription -  Adding a new version of the basket checkout that stops the user
from checking out if they don't have available balance in their wallet.
The authorisation is controlled using a paramater so we can use it internally
in service to service calls. Calls the wallet-service over HTTP to get the
balance and then to pay
*/

@RequestMapping("/v3/baskets/")
@RestController
public class CheckoutController {

    public static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    @Autowired
    public WalletService walletService;
    @Autowired
    public BasketRepository basketRepository;

    @PutMapping(value = "/new/{basketId}/v2/checkout/confirm")
    public String checkoutBasket(@PathVariable String basketid,
                                 @RequestParam boolean isInternal,
                                 Authentication auth,
                                 @RequestParam String walletId) {

        boolean canAccess = true;
        if (!isInternal || auth.getName() != null) {
            List<Basket> bList = basketRepository.findByUsername(auth.getName());
            boolean ownsBasket = false;
            bList.stream().forEach(b -> {
                if (b.getId().equals(basketid)) {
                    ownsBasket = true;
                }
            });
            if (!ownsBasket) canAccess = false;
        }


        if (!canAccess) return "NOT-ALLOWED";

        if (walletId == null) {
            var basketOptional = basketRepository.findById(basketid);
            walletId = basketOptional.get().getUserWallet();
        }

        Wallet wallet = walletService.getWallet(walletId);
        Optional.ofNullable(wallet).orElseThrow();
        double balance = wallet.getBalance();

        var basketOptional = basketRepository.findById(basketid);
        if (balance > basketOptional.get().getTotal()) {
            basketRepository.changeStatus(basketid, "SEND_FOR_DELIVERY");
            walletService.reduceBalance(walletId, basketOptional.get().getTotal());
        } else {
            return "INSUFFICIENT_FUNDS";
        }

        return "OK";

    }
}