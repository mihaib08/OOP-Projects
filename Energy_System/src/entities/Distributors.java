package entities;

import common.Constants;
import entity.Distributor;
import entity.Producer;
import output.Contract;
import output.OutDistributor;
import strategies.EnergyStrategy;
import strategies.EnergyStrategyFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static common.Constants.QUOTIENT;

/**
 * Distributors Database
 * Manages the operations of distributors
 */
public final class Distributors implements DistributorsObservers {
    /**
     * Singleton instance
     */
    private static final Distributors DISTRIBUTORS = new Distributors();

    /**
     * HashMap idDistributor (key) - distributor (value)
     */
    private HashMap<Integer, Distributor> distributors;

    /**
     * HashMap idDistributor (key) - no. consumers that are clients
     *                               of the distributor (value)
     */
    private HashMap<Integer, Integer> numConsumers;

    /**
     * HashMap idDistributor (key) - contractPrice (value)
     *     --> price of the contract after the last parsed update
     */
    private HashMap<Integer, Long> contractPrices;

    /**
     * HashMap id (key) - amount (value)
     *     --> total amount of monthly revenues received by a distributor
     */
    private HashMap<Integer, Long> revenues;

    /**
     * HashMap idDistributor (key) - the final list of contracts (value)
     */
    private HashMap<Integer, List<Contract>> distributorContracts = new HashMap<>();

    /**
     * Count the no. bankrupt distributors
     */
    private int noBankrupt;

    /**
     * Hashmap idDistributor (key) - list of producers (value)
     */
    private HashMap<Integer, List<Producer>> energyProducers;

    private EnergyStrategyFactory strategy = EnergyStrategyFactory.getInstance();

    /** Add a distributor to the simulation */
    public void addDistributor(final Distributor distributor) {
        distributors.put(distributor.getId(), distributor);
        numConsumers.put(distributor.getId(), 0);
        contractPrices.put(distributor.getId(), 0L);

        /* create the necessary strategy */
        EnergyStrategy energyStrategy = strategy.createStrategy(distributor.getProducerStrategy());
        distributor.setStrategy(energyStrategy);
    }

    /** Constructor - Private */
    private Distributors() { }

    public static Distributors getInstance() {
        return DISTRIBUTORS;
    }

    /**
     * Calculate the updated prices of the contracts
     *     -- only for not bankrupt distributors
     */
    void updatePrices() {
        /* profit */
        long pr;
        long contractPrice;

        for (int id : distributors.keySet()) {
            Distributor d = distributors.get(id);

            /* reset the revenues */
            revenues.put(id, 0L);

            /*
             * Check for the distributor to not be bankrupt
             *     --> still in the simulation
             */
            if (!d.isBankrupt()) {
                int num = numConsumers.get(id);
                if (num == 0) {
                    num++;
                }
                pr = Math.round(Math.floor(Constants.PRODUCTION_PERCENT * d.getProductionCost()));
                double aux = (double) d.getInfrastructureCost() / num;
                contractPrice = Math.round(Math.floor(aux) + d.getProductionCost() + pr);
                contractPrices.put(id, contractPrice);
            }
        }
    }

    /**
     * Find the distributor with the lowest installment
     */
    public Distributor getCheapestDistributor() {
        long min = Long.MAX_VALUE;
        Distributor res = null;

        for (int id : distributors.keySet()) {
            /* check if the current distributor is bankrupt */
            if (!checkBankruptcy(id)) {
                if (contractPrices.get(id) < min) {
                    min = contractPrices.get(id);
                    res = distributors.get(id);
                }
            }
        }
        return res;
    }

    /**
     * Decrease the no. consumers for a given distributor
     */
    public void decreaseNoConsumers(final int id) {
        int num = numConsumers.get(id);
        num--;
        numConsumers.put(id, num);
    }

    /**
     * Increase the no. consumers for a given distributor
     */
    public void increaseNoConsumers(final int id) {
        int num = numConsumers.get(id);
        num++;
        numConsumers.put(id, num);
    }

    /**
     * Check if a distributor is bankrupt or not
     *
     * @param id - distributor's id
     */
    public boolean checkBankruptcy(final int id) {
        Distributor d = distributors.get(id);
        return d.isBankrupt();
    }

    /** Get the current contract price of a distributor */
    long getContractPrice(final int id) {
        return contractPrices.get(id);
    }

    /** Process the monthly payments of each distributor */
    void processPayments() {
        for (int id : distributors.keySet()) {
            /* check bankruptcy */
            if (!checkBankruptcy(id)) {
                Distributor d = distributors.get(id);

                int num = numConsumers.get(id);
                long prod = (long) num * d.getProductionCost();
                long pay = d.getInfrastructureCost() + prod;

                long revenue = revenues.get(id);

                long currBudget = d.getBudget();

                currBudget -= pay;
                currBudget += revenue;
                if (currBudget < 0) {
                    d.setBankrupt(true);
                    noBankrupt++;
                }
                d.setBudget(currBudget);
            }
        }
    }

    /**
     * Add a revenue for a distributor
     * @param id - distributor's id
     * @param val - the revenue to be added
     */
    public void addRevenue(final int id, final long val) {
        long currRevenue = revenues.get(id);
        currRevenue += val;
        revenues.put(id, currRevenue);
    }

    /**
     * Update the costs for a given distributor
     */
    public void updateCosts(final int id, final int infrastructureCost) {
        Distributor d = distributors.get(id);
        d.setInfrastructureCost(infrastructureCost);
    }

    /**
     * Add a contract to the given distributor's list of contracts
     * @param id - distributor's id
     * @param contract - the contract to be added
     */
    void addContract(final int id, final Contract contract) {
        List<Contract> contractList;
        if (distributorContracts.containsKey(id)) {
            contractList = distributorContracts.get(id);
        } else {
            contractList = new ArrayList<>();
        }
        contractList.add(contract);
        distributorContracts.put(id, contractList);
    }

    /**
     * Get the list of each distributor's characteristics to be printed
     */
    public List<OutDistributor> getOutDistributors() {
        List<OutDistributor> outDistributors = new ArrayList<>();

        for (int id : distributors.keySet()) {
            Distributor d = distributors.get(id);
            List<Contract> contracts;
            if (!distributorContracts.containsKey(id)) {
                contracts = new ArrayList<>();
            } else {
                contracts = distributorContracts.get(id);
                contracts = sortContracts(contracts);
            }
            OutDistributor outDistributor = new OutDistributor(id, d.getEnergyNeededKW(),
                    contractPrices.get(id), d.getBudget(), d.getProducerStrategy(),
                    d.isBankrupt(), contracts);
            outDistributors.add(outDistributor);
        }
        return outDistributors;
    }

    /**
     * Sort a list of contracts ascending by
     *     --> 1st criteria : remainedContractMonths
     *     --> 2nd criteria : consumerId
     */
    private List<Contract> sortContracts(final List<Contract> contracts) {

        return contracts.stream()
                .sorted(Comparator.comparing(Contract::getConsumerId))
                .sorted(Comparator.comparingInt(Contract::getRemainedContractMonths))
                .collect(Collectors.toList());
    }

    /**
     * (Re)Initialise the fields
     */
    void initialiseEntries() {
        distributors = new HashMap<>();
        numConsumers = new HashMap<>();
        contractPrices = new HashMap<>();
        revenues = new HashMap<>();
        distributorContracts = new HashMap<>();
        noBankrupt = 0;
        energyProducers = new HashMap<>();
    }

    /**
     * Check if all distributors are bankrupt
     */
    public boolean checkAllBankrupt() {
        return noBankrupt == distributors.size();
    }

    /**
     * Choose the correspondent producers for each distributor
     *     -- depends on the strategy
     */
    public void chooseProducers(Producers producers, int currMonth) {
        for (int id : distributors.keySet()) {
            Distributor d = distributors.get(id);
            if (!checkBankruptcy(id) && d.isChangedProducer()) {
                if (currMonth > 0) {
                    /*
                     * eliminate the distributor
                     * from the current producers
                     */
                    List<Producer> currProducers = energyProducers.get(id);

                    producers.eliminateDistributor(id, currProducers);
                }

                EnergyStrategy energyStrategy = d.getStrategy();
                List<Producer> resProducers;
                int energyNeeded = d.getEnergyNeededKW();

                /* get the new list of producers based on the strategy */
                resProducers = energyStrategy.getEnergyProducers(energyNeeded, producers);
                energyProducers.put(id, resProducers);
                d.setChangedProducer(false);

                /* update the production cost for the current distributor */
                updateProductionCost(id);

                /* update the producers in resProducers */
                producers.updateProducers(id, resProducers);
            }
        }
    }

    /**
     * Update the production costs depending on energyProducers
     */
    public void updateProductionCost(int id) {
        List<Producer> dProducers = energyProducers.get(id);

        double cost = 0;
        for (Producer p : dProducers) {
            cost = cost + (p.getEnergyPerDistributor() * p.getPriceKW());
        }

        long productionCost = Math.round(Math.floor(cost / QUOTIENT));
        Distributor d = distributors.get(id);
        d.setProductionCost(productionCost);
    }

    @Override
    public void update(Producer p) {
        for (int id : energyProducers.keySet()) {
            List<Producer> ps = energyProducers.get(id);

            if (ps.contains(p)) {
                Distributor d = distributors.get(id);
                d.setChangedProducer(true);
            }
        }
    }
}
