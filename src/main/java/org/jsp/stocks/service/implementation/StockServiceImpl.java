package org.jsp.stocks.service.implementation;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.json.JSONObject;
import org.jsp.stocks.dto.AdminData;
import org.jsp.stocks.dto.Stock;
import org.jsp.stocks.dto.User;
import org.jsp.stocks.dto.UserStocksTransaction;
import org.jsp.stocks.repository.AdminDataRepository;
import org.jsp.stocks.repository.StockRepository;
import org.jsp.stocks.repository.UserRepository;
import org.jsp.stocks.service.StockService;
import org.jsp.stocks.service.UserStocksTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Service
public class StockServiceImpl implements StockService {

	DecimalFormat format = new DecimalFormat("#0.00");

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	UserStocksTransactionRepository transactionRepository;

	@Autowired
	StockRepository stockRepository;

	@Autowired
	AdminDataRepository dataRepository;

	@Value("${platformPercentage}")
	double platformPercentage;

	@Value("${razor-pay.api.key}")
	String razorpayKey;

	@Value("${razor-pay.api.secret}")
	String razorpaySecret;

	@Value("${admin.email}")
	String adminEmail;

	@Value("${admin.password}")
	String adminPassword;

	@Value("${stock.api.key}")
	String stockapikey;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JavaMailSender mailSender;

	@Override
	public String register(User user, Model model) {
		model.addAttribute("user", user);
		return "register.html";
	}

	@Override
	public String register(User user, BindingResult result, HttpSession session) {
		user.setEmail(user.getEmail().trim().toLowerCase());
		if (!user.getPassword().equals(user.getConfirmPassword()))
			result.rejectValue("confirmPassword", "error.confirmPassword",
					"* Password and Confirm Password are Not Matching");
		if (user.getDob() != null) {
			if (LocalDate.now().getYear() - user.getDob().getYear() < 18)
				result.rejectValue("dob", "error.dob", "* You should be 18+ to Create Account here");
		}
		if (userRepository.existsByEmail(user.getEmail()))
			result.rejectValue("email", "error.email", "* Email should be Unique");

		if (userRepository.existsByMobile(user.getMobile()))
			result.rejectValue("mobile", "error.mobile", "* Mobile Number should be Unique");

		if (result.hasErrors()) {
			return "register.html";
		} else {
			user.setOtp(generateOtp());
			sendEmail(user);
			user.setPassword(AES.encrypt(user.getPassword()));

			User savedUser = userRepository.save(user);

			session.setAttribute("pass",
					"Otp Sent Success, check your email and Enter OTP");

			return "redirect:/otp/" + savedUser.getId();
		}
	}

	@Override
	public String verifyOtp(int id, int otp, HttpSession session) {
		User user = userRepository.findById(id).get();
		if (user.getOtp() == otp) {
			user.setVerified(true);
			user.setOtp(0);
			userRepository.save(user);
			session.setAttribute("pass", "Account Created Success, Welcome " + user.getName());
			return "redirect:/login";
		} else {
			session.setAttribute("fail", "Invalid Otp Try Again");
			return "redirect:/otp/" + id;
		}
	}

	@Override
	public String login(String email, String password, HttpSession session) {
		session.removeAttribute("user");
		session.removeAttribute("admin");
		
		email = email.trim().toLowerCase();
		password = password.trim();

		if (email.equalsIgnoreCase(adminEmail) && password.equals(adminPassword)) {
			session.setAttribute("admin", "admin");
			session.setAttribute("pass", "Login Success - Welcome Admin");
			return "redirect:/";
		}

		Optional<User> userOptional = userRepository.findByEmail(email);
		if (userOptional.isEmpty()) {
			session.setAttribute("fail", "Invalid Email");
			return "redirect:/login";
		} else {
			User user = userOptional.get();
			String decryptedPassword = AES.decrypt(user.getPassword());
			if (decryptedPassword != null && decryptedPassword.equals(password)) {
				if (user.isVerified()) {
					session.setAttribute("user", user);
					session.setAttribute("pass", "Login Success, Welcome " + user.getName());
					return "redirect:/";
				} else {
					user.setOtp(generateOtp());
					sendEmail(user);
					userRepository.save(user);
					session.setAttribute("fail", "First Complete Verification in order to Login");
					return "redirect:/otp/" + user.getId();
				}
			} else {
				session.setAttribute("fail", "Invalid Password");
				return "redirect:/login";
			}
		}
	}

	@Override
	public String logout(HttpSession session) {
		User user = (User) session.getAttribute("user");
		if (user != null)
			session.setAttribute("pass", "Logout Success, Sad to see you go Bye " + user.getName());
		session.removeAttribute("user");
		session.removeAttribute("admin");
		return "redirect:/";
	}

	public void removeMessage() {
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
				.currentRequestAttributes();
		HttpServletRequest req = attributes.getRequest();
		HttpSession session = req.getSession();
		session.removeAttribute("pass");
		session.removeAttribute("fail");
	}

	int generateOtp() {
		return new Random().nextInt(100000, 1000000);
	}

	void sendEmail(User user) {
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
		HttpSession session = attributes.getRequest().getSession();
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message);
		try {
			helper.setFrom("maheshbiradar698@gmail.com", "NammaStocks");
			helper.setTo(user.getEmail());
			helper.setSubject("OTP for Account Creation");
			helper.setText("<div style='background-color: #f8f9fa; padding: 20px; border-radius: 10px; font-family: Arial, sans-serif;'>"
			+ "<h1 style='color: #007bff; text-align: center; margin-bottom: 20px;'>Namma Stocks</h1>"
			+ "<p style='font-size: 16px; color: #333;'>Hello " + user.getName() + ",</p>"
			+ "<p style='font-size: 16px; color: #333; margin-bottom: 20px;'>Thank you for registering with us. Please use the following OTP to verify your account:</p>"
			+ "<div style='background-color: #e9ecef; padding: 15px; border-radius: 5px; text-align: center; margin: 20px 0;'>"
			+ "<h2 style='color: #28a745; margin: 0;'>" + user.getOtp() + "</h2>"
			+ "</div>"
			+ "<p style='font-size: 14px; color: #6c757d; text-align: center;'>This OTP is valid for a limited time only.</p>"
			+ "</div>", true);
			mailSender.send(message);
		}catch (Exception e) {
		    System.err.println("Unable to Send Email");
		    e.printStackTrace();

		    System.out.println(
		        "Hello " + user.getName()
		        + " Your OTP is : " + user.getOtp()
		    );
		}
	}

	@Override
	public String addStock(HttpSession session) {
		if (session.getAttribute("admin") != null)
			return "add-stock.html";
		else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String addStock(HttpSession session, Stock stock) {

	    if (session.getAttribute("admin") != null) {

	        stock.setTicker(
	                stock.getTicker()
	                        .trim()
	                        .toUpperCase());

	        boolean flag = updateStockFromAPI(stock);

	        if (flag) {

	            if (stockRepository.existsById(
	                    stock.getTicker())) {

	                session.setAttribute(
	                        "fail",
	                        "Stock Already Present for "
	                                + stock.getTicker());

	                return "redirect:/";

	            } else {

	                stockRepository.save(stock);

	                session.setAttribute(
	                        "pass",
	                        "Stock Added Success for "
	                                + stock.getCompanyName());

	                return "redirect:/";
	            }

	        } else {

	            session.setAttribute(
	                    "fail",
	                    "Stock Not Found for "
	                            + stock.getTicker());

	            return "redirect:/";
	        }

	    } else {

	        session.setAttribute(
	                "fail",
	                "Invalid Session, Login First");

	        return "redirect:/login";
	    }
	}
	public boolean updateStockFromAPI(Stock stock) {
		if (stockapikey == null || stockapikey.isEmpty() || stockapikey.equals("YOUR_ALPHA_VANTAGE_KEY")) {
			return false;
		}

		try {
			String url = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=" + stock.getTicker()
					+ "&apikey=" + stockapikey;
			String response = restTemplate.getForObject(url, String.class);
			JSONObject jsonObject = new JSONObject(response);

			if (jsonObject.has("Global Quote")
			        && jsonObject.getJSONObject("Global Quote")
			                .length() > 0) {

			    JSONObject quote =
			            jsonObject.getJSONObject(
			                    "Global Quote");

			    stock.setPrice(
			            formatNumber(
			                    quote.getString(
			                            "05. price")));

			    stock.setChanges(
			            Double.parseDouble(
			                    quote.getString(
			                            "09. change")));

			    if (stock.getCompanyName() == null
			            || stock.getCompanyName()
			                    .isEmpty()) {

			        stock.setCompanyName(
			                fetchCompanyName(
			                        stock.getTicker()));
			    }

			    if (stock.getQuantity() == 0) {
			        stock.setQuantity(1000.0);
			    }

			    return true;
			}

			return false;

			} catch (Exception e) {
			    e.printStackTrace();
			    return false;
			}
	}

	private String fetchCompanyName(String ticker) {
		try {
			String url = "https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords=" + ticker + "&apikey="
					+ stockapikey;
			String response = restTemplate.getForObject(url, String.class);
			JSONObject jsonObject = new JSONObject(response);
			if (jsonObject.has("bestMatches")) {
				return jsonObject.getJSONArray("bestMatches").getJSONObject(0).getString("2. name");
			}
		} catch (Exception e) {
		}
		return ticker + " Corporation";
	}

	private boolean mockUpdate(Stock stock) {
		// Mock Mode: Simulating API response
		if (stock.getPrice() == 0) {
			stock.setPrice(100.00);
			stock.setQuantity(1000.00);
			stock.setChanges(1.5);
			if (stock.getCompanyName() == null || stock.getCompanyName().isEmpty()) {
				stock.setCompanyName(stock.getTicker() + " Corporation");
			}
		} else {
			// Simulate slight price fluctuation
			double fluctuation = (new Random().nextDouble() - 0.5) * 2; // -1 to +1
			stock.setPrice(formatNumber(String.valueOf(stock.getPrice() + fluctuation)));
		}
		return true;
	}

	@Override
	public String fetchStocks(HttpSession session, Model model) {
		if (session.getAttribute("admin") != null) {
			List<Stock> stocks = stockRepository.findAll();
			if (stocks.isEmpty()) {
				session.setAttribute("fail", "No Stocks PResent");
				return "redirect:/";
			} else {
				model.addAttribute("stocks", stocks);
				return "admin-view-stocks.html";
			}
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String deleteStock(String ticker, HttpSession session) {
		if (session.getAttribute("admin") != null) {
			stockRepository.deleteById(ticker);
			session.setAttribute("pass", "Stock deleted Success");
			return "redirect:/manage-stocks";
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	public Double formatNumber(String number) {
		return Double.parseDouble(format.format(Double.parseDouble(number)));
	}

	@Override
	public String viewStocks(HttpSession session, Model model, String company) {
		if (session.getAttribute("user") != null) {
			List<Stock> stocks;
			if (company == null)
				stocks = stockRepository.findAll();
			else
				stocks = stockRepository.findByCompanyNameLike("%" + company + "%");

			if (stocks.isEmpty()) {
				session.setAttribute("fail", "No Stocks Present");
				return "redirect:/";
			} else {
				model.addAttribute("stocks", stocks);
				return "user-view-stocks.html";
			}
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String viewWallet(HttpSession session, Model model) {
		if (session.getAttribute("user") != null) {
			User user = (User) session.getAttribute("user");
			model.addAttribute("amount", user.getAmount());
			return "wallet.html";
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String rechargeWallet(double amount, HttpSession session, Model model) throws RazorpayException {
		if (session.getAttribute("user") != null) {

			RazorpayClient client = new RazorpayClient(razorpayKey, razorpaySecret);
			System.out.println(razorpayKey);
			System.out.println(razorpaySecret);
			JSONObject json = new JSONObject();
			json.put("amount", amount * 100);
			json.put("currency", "INR");

			Order order = client.orders.create(json);

			User user = (User) session.getAttribute("user");
			model.addAttribute("orderId", order.get("id"));
			model.addAttribute("key", razorpayKey);
			model.addAttribute("orderAmount", order.get("amount"));
			model.addAttribute("currency", order.get("currency"));
			model.addAttribute("userName", user.getName());
			model.addAttribute("userEmail", user.getEmail());
			model.addAttribute("userMobile", user.getMobile());
			return "payment.html";
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String paymentSuccess(double amount, HttpSession session) {
		if (session.getAttribute("user") != null) {
			User user = (User) session.getAttribute("user");
			user.setAmount(Double.parseDouble(format.format(user.getAmount() + (amount / 100))));
			session.setAttribute("user", userRepository.save(user));
			session.setAttribute("pass", "Amount Added Successfully");
			return "redirect:/wallet";
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String viewStock(HttpSession session, Model model, String ticker) {
		if (session.getAttribute("user") != null) {
			Optional<Stock> opStock = stockRepository.findById(ticker);
			if (opStock.isEmpty()) {
				session.setAttribute("fail", "Stock Not Found");
				return "redirect:/";
			}
			Stock stock = opStock.get();
			if (updateStockFromAPI(stock))
				stockRepository.save(stock);

			model.addAttribute("stock", stock);
			return "view-stock.html";
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String buyStock(String ticker, double quantity, HttpSession session, Model model) {
		if (session.getAttribute("user") != null) {
			Optional<Stock> opStock = stockRepository.findById(ticker);
			if (opStock.isEmpty()) {
				session.setAttribute("fail", "Stock Not Found");
				return "redirect:/";
			}
			Stock stock = opStock.get();
			if (quantity <= stock.getQuantity()) {
				double totalPrice = stock.getPrice() * quantity;
				User user = (User) session.getAttribute("user");
				double walletAmount = user.getAmount();
				model.addAttribute("totalPrice", totalPrice);
				model.addAttribute("platformPercentage", platformPercentage);
				model.addAttribute("wallet", walletAmount);
				model.addAttribute("ticker", ticker);
				model.addAttribute("quantity", quantity);
				return "confirm-buy.html";
			} else {
				session.setAttribute("fail", "Out of Limit");
				return "redirect:/";
			}
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String confirmPurchase(HttpSession session, String ticker, double quantity, double price) {
		if (session.getAttribute("user") != null) {
			User user = (User) session.getAttribute("user");
			double walletPrice = user.getAmount();
			Optional<Stock> opStock = stockRepository.findById(ticker);
			if (opStock.isEmpty()) {
				session.setAttribute("fail", "Stock Not Found");
				return "redirect:/";
			}
			Stock stock = opStock.get();
			double platformFee = price * platformPercentage;

			stock.setQuantity(stock.getQuantity() - quantity);
			stockRepository.save(stock);

			user.setAmount(Double.parseDouble(format.format(walletPrice - (price + platformFee))));
			userRepository.save(user);
			Optional<AdminData> opData = dataRepository.findById(1);
			AdminData data;
			if (opData.isPresent()) {
				data = opData.get();
			} else {
				data = new AdminData();
			}
			data.setPlatformFeePercentage(platformPercentage);
			data.setTotalPlatformFee(data.getTotalPlatformFee() + platformFee);
			data.setTotalStocksBought(data.getTotalStocksBought() + quantity);
			data.setTotalTransaction(data.getTotalTransaction() + price);
			dataRepository.save(data);

			List<UserStocksTransaction> transactions = user.getTransactions();
			boolean flag = true;

			for (UserStocksTransaction transaction : transactions) {
				if (transaction.getStock_ticker().equals(ticker)) {
					double totalOldInvestment =
					        transaction.getPrice()
					        * transaction.getQuantity();

					double totalNewInvestment =
					        totalOldInvestment
					        + price;

					double totalQuantity =
					        transaction.getQuantity()
					        + quantity;

					transaction.setQuantity(
					        totalQuantity);

					transaction.setPrice(
					        totalNewInvestment
					        / totalQuantity);

					transactionRepository.save(
					        transaction);
					flag = false;
					break;
				}
			}
			if (flag) {
				UserStocksTransaction transaction = new UserStocksTransaction();
				transaction.setStock_ticker(ticker);
				transaction.setPrice(price / quantity);
				transaction.setQuantity(quantity);
				transactions.add(transaction);

			}
			session.setAttribute("user", userRepository.save(user));
			session.setAttribute("pass", "Stock Purchased Success");
			return "redirect:/";
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String viewOverview(HttpSession session, Model model) {
		if (session.getAttribute("admin") != null) {
			Optional<AdminData> data = dataRepository.findById(1);
			if (data.isPresent()) {
				model.addAttribute("data", data.get());
				return "overview.html";
			} else {
				session.setAttribute("fail", "No Details Present");
				return "redirect:/";
			}
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String viewPortfolio(
	        HttpSession session,
	        Model model) {

	    if (session.getAttribute("user")
	            != null) {

	        User user =
	                (User) session.getAttribute(
	                        "user");

	        List<UserStocksTransaction>
	                transactions =
	                user.getTransactions();

	        if (transactions.isEmpty()) {

	            session.setAttribute(
	                    "fail",
	                    "No Data to display in Portfolio");

	            return "redirect:/";

	        } else {

	            double totalInvested =
	                    transactions.stream()
	                    .mapToDouble(
	                        x -> x.getPrice()
	                        * x.getQuantity())
	                    .sum();

	            double currentValue = 0;

	            double totalProfitLoss = 0;

	            for (UserStocksTransaction
	                    transaction :
	                    transactions) {

	                Optional<Stock> opStock =
	                        stockRepository.findById(
	                                transaction.getStock_ticker());

	                if (opStock.isPresent()) {

	                    Stock stock =
	                            opStock.get();

	                    updateStockFromAPI(stock);

	                    stockRepository.save(stock);

	                    double currentStockValue =
	                            stock.getPrice()
	                            * transaction.getQuantity();

	                    currentValue +=
	                            currentStockValue;

	                    double investedAmount =
	                            transaction.getPrice()
	                            * transaction.getQuantity();

	                    double profitLoss =
	                            currentStockValue
	                            - investedAmount;

	                    transaction.setProfitLoss(
	                            profitLoss);

	                    totalProfitLoss +=
	                            profitLoss;
	                }
	            }

	            model.addAttribute(
	                    "totalInvested",
	                    totalInvested);

	            model.addAttribute(
	                    "currentValue",
	                    currentValue);

	            model.addAttribute(
	                    "profitLoss",
	                    totalProfitLoss);

	            model.addAttribute(
	                    "transactions",
	                    transactions);

	            return "portfolio.html";
	        }

	    } else {

	        session.setAttribute(
	                "fail",
	                "Invalid Session, Login First");

	        return "redirect:/login";
	    }
	}
	@Override
	public String viewSell(String ticker, HttpSession session, Model model) {
		if (session.getAttribute("user") != null) {
			Optional<Stock> opStock = stockRepository.findById(ticker);
			if (opStock.isEmpty()) {
				session.setAttribute("fail", "Stock Not Found");
				return "redirect:/";
			}
			model.addAttribute("stock", opStock.get());
			return "enter-quantity.html";
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}

	@Override
	public String sellStocks(double quantity, String ticker, HttpSession session) {
		if (session.getAttribute("user") != null) {
			User user = (User) session.getAttribute("user");
			Optional<Stock> opStock = stockRepository.findById(ticker);
			if (opStock.isEmpty()) {
				session.setAttribute("fail", "Stock Not Found");
				return "redirect:/";
			}
			Stock stock = opStock.get();
			List<UserStocksTransaction> transactions = user.getTransactions();
			for (UserStocksTransaction transaction : transactions) {
				if (transaction.getStock_ticker().equals(ticker)) {
					if (quantity > transaction.getQuantity()) {
						session.setAttribute("fail", "You dont have enough quantity");
						return "redirect:/portfolio";
					} else {
						if (transaction.getQuantity() > quantity) {
							transaction.setQuantity(transaction.getQuantity() - quantity);
							transactionRepository.save(transaction);
							double salePrice = stock.getPrice() * quantity;
							double platformFee = salePrice * platformPercentage;
							user.setAmount(Double.parseDouble(format.format(user.getAmount() + (salePrice - platformFee))));
							userRepository.save(user);
						} else {
							transactions.remove(transaction);
							double salePrice = stock.getPrice() * quantity;
							double platformFee = salePrice * platformPercentage;
							user.setAmount(Double.parseDouble(format.format(user.getAmount() + (salePrice - platformFee))));
							userRepository.save(user);
							transactionRepository.deleteById(transaction.getId());
						}

						stock.setQuantity(stock.getQuantity() + quantity);
						stockRepository.save(stock);
						session.setAttribute("user", userRepository.findById(user.getId()).get());
						session.setAttribute("pass", "Stock Sold Success");

						AdminData data = dataRepository.findById(1).orElse(new AdminData());
						double salePrice = stock.getPrice() * quantity;
						double platformFee = salePrice * platformPercentage;
						data.setTotalPlatformFee(data.getTotalPlatformFee() + platformFee);
						data.setTotalStocksSold(data.getTotalStocksSold() + quantity);
						data.setTotalTransaction(data.getTotalTransaction() + salePrice);
						dataRepository.save(data);
						return "redirect:/portfolio";
					}
				}
			}
			session.setAttribute("fail", "Something wrong with Stocks, Try after Sometime");
			return "redirect:/";
		} else {
			session.setAttribute("fail", "Invalid Session, Login First");
			return "redirect:/login";
		}
	}
	@Override
	public String addMoney(
	        double amount,
	        HttpSession session) {

	    if (session.getAttribute("user")
	            != null) {

	        User user =
	                (User) session.getAttribute("user");

	        user.setAmount(
	                user.getAmount() + amount);

	        session.setAttribute(
	                "user",
	                userRepository.save(user));

	        session.setAttribute(
	                "pass",
	                "Money Added Successfully");

	        return "redirect:/wallet";
	    }

	    session.setAttribute(
	            "fail",
	            "Login First");

	    return "redirect:/login";
	}
}
